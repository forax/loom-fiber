package fr.umlv.loom.continuation;

import fr.umlv.loom.executor.UnsafeExecutors;
import jdk.incubator.concurrent.ExtentLocal;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Continuation {
  private enum State { NEW, RUNNING, WAITED, TERMINATED }

  private static final ExtentLocal<Continuation> CONTINUATION_SCOPE_LOCAL = ExtentLocal.newInstance();

  private final Runnable runnable;
  private final Thread owner;
  private State state = State.NEW;
  private final ReentrantLock lock = new ReentrantLock();
  private final Condition condition = lock.newCondition();

  public Continuation(Runnable runnable) {
    this.runnable = runnable;
    this.owner = Thread.currentThread();
  }

  public void run() {
    if (Thread.currentThread() != owner) {
      throw new IllegalStateException();
    }
    switch (state) {
      case NEW -> {
        state = State.RUNNING;
        var executor = UnsafeExecutors.virtualThreadExecutor(Runnable::run);
        executor.execute(() -> {
          ExtentLocal.where(CONTINUATION_SCOPE_LOCAL, this, runnable);
          state = State.TERMINATED;
        });
      }
      case WAITED -> {
        state = State.RUNNING;
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
