package fr.umlv.loom.executor;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.StructuredExecutor;
import java.util.concurrent.StructuredExecutor.ShutdownOnSuccess;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class VirtualThreadExecutor implements ExecutorService {
  private static final VarHandle STATE;
  static {
    var lookup = MethodHandles.lookup();
    try {
      STATE = lookup.findVarHandle(VirtualThreadExecutor.class, "state", int.class);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }

  private final StructuredExecutor executor;
  private final ExecutorService executorOfExecutor = Executors.newSingleThreadExecutor();
  private volatile int state;

  private static final int RUNNING    = 0;
  private static final int SHUTDOWN   = 1;
  private static final int TERMINATED = 2;

  public VirtualThreadExecutor() {
    executor = postSync(StructuredExecutor::open);
  }

  private void checkShutdownState() {
    if (state >= SHUTDOWN) {
      throw new RejectedExecutionException();
    }
  }

  @SuppressWarnings("unchecked")   // very wrong but works
  private static <T extends Throwable> AssertionError rethrow(Throwable cause) throws T {
    throw (T) cause;
  }

  private void postAsync(Runnable runnable) {
    executorOfExecutor.execute(runnable);
  }

  private <V> V postSync(Callable<V> callable)   {
    var future = executorOfExecutor.submit(callable);
    try {
      return future.get();
    } catch (ExecutionException e) {
      var cause = e.getCause();
      throw rethrow(cause);
    } catch (InterruptedException e) {
      throw rethrow(e);
    }
  }

  @Override
  public void shutdown() {
    if (STATE.compareAndSet(this, RUNNING, SHUTDOWN)) {
      postAsync(() -> {
        if (state == TERMINATED) {
          return;
        }
        try {
          executor.join();
        } catch (InterruptedException e) {
          throw new AssertionError(e);
        }
        state = TERMINATED;
        executor.close();
        executorOfExecutor.shutdown();
      });
    }
  }

  @Override
  public List<Runnable> shutdownNow() {
    if (STATE.compareAndSet(this, RUNNING, SHUTDOWN)) {
      postSync(() -> {
        if (state == TERMINATED) {
          return null;
        }
        executor.shutdown();
        executor.join();
        state = TERMINATED;
        executor.close();
        executorOfExecutor.shutdown();
        return null;
      });
    }
    return List.of();
  }

  @Override
  public boolean isShutdown() {
    return state >= SHUTDOWN;
  }

  @Override
  public boolean isTerminated() {
    return state == TERMINATED;
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) {
    if (state == TERMINATED) {
      return false;
    }
    var deadline = Instant.now().plus(Duration.ofNanos(unit.toNanos(timeout)));
    return postSync(() -> {
      if (state == TERMINATED) {
        return false;
      }
      var result = true;
      try {
        executor.joinUntil(deadline);
      } catch (TimeoutException e) {
        result = false;
      }
      executor.shutdown();
      state = TERMINATED;
      executor.close();
      executorOfExecutor.shutdown();
      return result;
    });
  }

  @Override
  public void execute(Runnable command) {
    submit(command);
  }

  @Override
  public <T> Future<T> submit(Callable<T> task) {
    checkShutdownState();
    return postSync(() -> {
      checkShutdownState();
      return executor.fork(task);
    });
  }

  @Override
  public <T> Future<T> submit(Runnable task, T result) {
    return submit(() -> {
      task.run();
      return result;
    });
  }

  @Override
  public Future<?> submit(Runnable task) {
    return submit(task, null);
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
    checkShutdownState();
    return postSync(() -> {
      checkShutdownState();
      try(var executor = StructuredExecutor.open()) {
        var list = tasks.stream().map(executor::fork).toList();
        executor.join();
        return list;
      }
    });
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
    checkShutdownState();
    var deadline = Instant.now().plus(Duration.ofNanos(unit.toNanos(timeout)));
    return postSync(() -> {
      checkShutdownState();
      try(var executor = StructuredExecutor.open()) {
        var futures = tasks.stream().map(executor::fork).toList();
        try {
          executor.joinUntil(deadline);
        } catch(TimeoutException e) {
          return futures.stream().filter(Future::isDone).toList();
        }
        return futures;
      }
    });
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
    checkShutdownState();
    return postSync(() -> {
      checkShutdownState();
      try(var executor = StructuredExecutor.open()) {
        var handler = new ShutdownOnSuccess<T>();
        tasks.forEach(callable -> executor.fork(callable, handler));
        executor.join();
        return handler.result();
      }
    });
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    checkShutdownState();
    var deadline = Instant.now().plus(Duration.ofNanos(unit.toNanos(timeout)));
    return postSync(() -> {
      checkShutdownState();
      try(var executor = StructuredExecutor.open()) {
        var handler = new ShutdownOnSuccess<T>();
        tasks.forEach(callable -> executor.fork(callable, handler));
        executor.joinUntil(deadline);
        return handler.result();
      }
    });
  }
}
