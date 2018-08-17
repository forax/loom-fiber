package fr.umlv.loom;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;

public interface Task<T> extends Future<T> {
  T await() throws CancellationException;
  T awaitNanos(long nanos) throws CancellationException, TimeoutException;
  
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
    
    private final Fiber fiber;
    private volatile Object result;  // null -> CANCELLED or null -> value | $$$(exception)
    
    TaskImpl(Function<Runnable, Fiber> execution, Supplier<? extends T> supplier) {
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
    public T await() {
      fiber.await();
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
    public T awaitNanos(long nanos) throws TimeoutException {
      fiber.awaitNanos(nanos);
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
    public T get() throws CancellationException, ExecutionException {
      fiber.await();
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
    public T get(long timeout, TimeUnit unit) throws TimeoutException, ExecutionException {
      fiber.awaitNanos(unit.toNanos(timeout));
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
    return new TaskImpl<>(Fiber::execute, supplier);
  }
  
  public static <T> Task<T> async(Executor executor, Supplier<? extends T> supplier) {
    return new TaskImpl<>(runnable -> Fiber.execute(executor, runnable), supplier);
  }
}
