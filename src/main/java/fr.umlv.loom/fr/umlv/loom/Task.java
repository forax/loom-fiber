package fr.umlv.loom;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;

public interface Task<T> extends Future<T> {
  T join() throws CancellationException;
  T await(Duration duration) throws CancellationException, TimeoutException;
  
  final class TaskImpl<T> implements Task<T> {
    private static final Object CANCELLED = new Object();
    private static final VarHandle RESULT_HANDLE;
    static {
      try {
        RESULT_HANDLE = MethodHandles.lookup().findVarHandle(TaskImpl.class, "result", Object.class);
      } catch (NoSuchFieldException | IllegalAccessException e) {
        throw new AssertionError(e);
      }
    }
    
    private final static class $$$<E extends Throwable> {
      final E throwable;

      $$$(E throwable) {
        this.throwable = throwable;
      }
    }
    
    private final Fiber<?> fiber;
    private volatile Object result;  // null -> CANCELLED or null -> value | $$$(exception)
    
    TaskImpl(Function<Runnable, Fiber<?>> execution, Supplier<? extends T> supplier) {
      fiber = execution.apply(() -> {
        Object result;
        try {
          result = supplier.get();
        } catch(Error e) {  // don't capture errors, only checked and unchecked exceptions
          throw e;
        } catch(Throwable e) {
          result = new $$$<>(e);
        }
        setResultIfNull(Objects.requireNonNull(result));
      });
    }
    
    private boolean setResultIfNull(Object result) {
      return RESULT_HANDLE.compareAndSet(this, (Object)null, result);
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public T join() {
      try {
				fiber.join();
			} catch (InterruptedException e) {
				throw new CompletionException(e);
			}
      Object result = this.result;
      if (result == CANCELLED) {
        throw new CancellationException();
      }
      if (result instanceof $$$<?>) {
        throw (($$$<RuntimeException>)result).throwable;
      }
      return (T)result;
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public T await(Duration duration) throws TimeoutException {
    	try {
        fiber.awaitTermination(duration);
    	} catch(InterruptedException e) {
    		throw new CompletionException(e);
    	}
      if (setResultIfNull(CANCELLED)) {
        throw new TimeoutException();
      }
      Object result = this.result;
      if (result == CANCELLED) {
        throw new CancellationException();
      }
      if (result instanceof $$$<?>) {
        throw (($$$<RuntimeException>)result).throwable;
      }
      return (T)result;
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public T get() throws CancellationException, ExecutionException, InterruptedException {
      fiber.join();
      Object result = this.result;
      if (result == CANCELLED) {
        throw new CancellationException();
      }
      if (result instanceof $$$<?>) {
        throw new ExecutionException((($$$<?>)result).throwable);
      }
      return (T)result;
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public T get(long timeout, TimeUnit unit) throws TimeoutException, ExecutionException, InterruptedException {
      fiber.awaitTermination(Duration.of(timeout, unit.toChronoUnit()));
      if (setResultIfNull(CANCELLED)) {
        throw new TimeoutException();
      }
      Object result = this.result;
      if (result == CANCELLED) {
        throw new CancellationException();
      }
      if (result instanceof $$$<?>) {
        throw new ExecutionException((($$$<?>)result).throwable);
      }
      return (T)result;
    }
    
    @Override
    public boolean isDone() {
      return result != null;
    }
    
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      return setResultIfNull(CANCELLED);
    }
    
    @Override
    public boolean isCancelled() {
      return result == CANCELLED;
    }
  }
  
  public static <T> Task<T> async(Supplier<? extends T> supplier) {
    return new TaskImpl<>(runnable -> FiberScope.background().schedule(runnable), supplier);
  }
  
  public static <T> Task<T> async(Executor executor, Supplier<? extends T> supplier) {
    return new TaskImpl<>(runnable -> FiberScope.background().schedule(executor, runnable), supplier);
  }
}
