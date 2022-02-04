package fr.umlv.loom.continuation;

import fr.umlv.loom.executor.UnsafeExecutors;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Continuation {
  private enum State { NEW, RUNNING, WAITED, TERMINATED }

  private static final ScopeLocal<Continuation> CONTINUATION_SCOPE_LOCAL = ScopeLocal.newInstance();
  private static final VarHandle STATE_VH;
  static {
    try {
      var lookup = MethodHandles.lookup();
      STATE_VH = lookup.findVarHandle(Continuation.class, "state", State.class);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }

  private final Runnable runnable;
  private volatile State state = State.NEW;
  private final ReentrantLock lock = new ReentrantLock();
  private final Condition condition = lock.newCondition();

  public Continuation(Runnable runnable) {
    this.runnable = runnable;
  }

  public void start() {
    switch (state) {
      case NEW -> {
        if (!STATE_VH.compareAndSet(this, State.NEW, State.RUNNING)) {
          throw new IllegalStateException();
        }
        var executor = UnsafeExecutors.virtualThreadExecutor(Runnable::run);
        executor.execute(() -> {
          ScopeLocal.where(CONTINUATION_SCOPE_LOCAL, this, runnable);
          state = State.TERMINATED;
        });
      }
      case WAITED -> {
        if (!STATE_VH.compareAndSet(this, State.WAITED, State.RUNNING)) {
          throw new IllegalStateException();
        }
        lock.lock();
        try {
          condition.signal();
        } finally {
          lock.unlock();
        }
      }
      case RUNNING, TERMINATED -> throw new IllegalStateException();
    }
  }

  public static void yield() {
    if (!CONTINUATION_SCOPE_LOCAL.isBound()) {
      throw new IllegalStateException();
    }
    var continuation = CONTINUATION_SCOPE_LOCAL.get();
    continuation.lock.lock();
    try {
      continuation.state = State.WAITED;
      continuation.condition.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      continuation.lock.unlock();
    }
  }
}
