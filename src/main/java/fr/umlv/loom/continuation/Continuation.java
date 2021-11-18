package fr.umlv.loom.continuation;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Continuation {
  private enum State { NEW, STARTED, TERMINATED }

  private static final VarHandle STATE, PREVIOUS_CONTINUATION;
  static {
    var lookup = MethodHandles.lookup();
    try {
      STATE = lookup.findVarHandle(Continuation.class, "state", State.class);
      PREVIOUS_CONTINUATION = lookup.findVarHandle(Continuation.class, "previousContinuation", Continuation.class);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }
  private static final ScopeLocal<Continuation> CURRENT_CONTINUATION = ScopeLocal.newInstance();
  private final Runnable runnable;
  private volatile State state;
  private volatile Continuation previousContinuation;
  private final ReentrantLock parkedLock = new ReentrantLock();
  private final Condition parkedCondition = parkedLock.newCondition();
  private boolean parked;

  public Continuation(Runnable runnable) {
    this.runnable = runnable;
    this.state = State.NEW;
  }

  public void start() {
    var state = this.state;
    if (state == State.TERMINATED) {
      throw new IllegalStateException("continuation terminated");
    }
    var currentContinuation = CURRENT_CONTINUATION.isBound()?
        CURRENT_CONTINUATION.get():
        new Continuation(null); // fake continuation
    if (!PREVIOUS_CONTINUATION.compareAndSet(this, null, currentContinuation)) {
      throw new IllegalStateException("continuation already running");
    }
    if (state == State.NEW && STATE.compareAndSet(this, State.NEW, State.STARTED)) {
      Thread.ofVirtual().start(() -> {
        ScopeLocal.where(CURRENT_CONTINUATION, this, runnable);
        this.state = State.TERMINATED;
        previousContinuation.unpark();
        previousContinuation = null;
      });
      currentContinuation.park();
      return;
    }
    this.unpark();
    currentContinuation.park();
  }

  public static void yield() {
    if (!CURRENT_CONTINUATION.isBound()) {
      throw new IllegalStateException("no current continuation");
    }
    var continuation = CURRENT_CONTINUATION.get();
    continuation.previousContinuation.unpark();
    continuation.previousContinuation = null;
    continuation.park();
  }

  private void park() {
    boolean interrupted = false;
    parkedLock.lock();
    try {
      parked = true;
      while(parked) {
        try {
          parkedCondition.await();
        } catch (InterruptedException e) {
          interrupted = true;
        }
      }
    } finally {
      parkedLock.unlock();
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  private void unpark() {
    parkedLock.lock();
    try {
      parked = false;
      parkedCondition.signal();
    } finally {
      parkedLock.unlock();
    }
  }
}
