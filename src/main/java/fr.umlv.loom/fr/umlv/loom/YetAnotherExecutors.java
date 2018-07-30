package fr.umlv.loom;

import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class YetAnotherExecutors {
  public static <T> Future<T> execute(Supplier<? extends T> supplier) {
    return new Future<>() {
      private final Fiber fiber = Fiber.execute(() -> {
        result = Objects.requireNonNull(supplier.get());
      });
      volatile T result;
      
      @Override
      public boolean isDone() {
        return result != null;
      }
      @Override
      public T get() {
        fiber.await();
        return result;
      }
      @Override
      public T get(long timeout, TimeUnit unit) throws IllegalStateException {
        fiber.awaitNanos(unit.toNanos(timeout));
        T result = this.result;
        if (result == null) {
          throw new IllegalStateException("timeout");
        }
        return result;
      }
      
      @Override
      public boolean isCancelled() {
        return false;
      }
      @Override
      public boolean cancel(boolean mayInterruptIfRunning) {
        throw new UnsupportedOperationException();
      }
    };
  }
}
