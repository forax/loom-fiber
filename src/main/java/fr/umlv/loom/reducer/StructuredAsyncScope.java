package fr.umlv.loom.reducer;

import fr.umlv.loom.reducer.StructuredAsyncScope.Result.State;
import jdk.incubator.concurrent.StructuredTaskScope;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

public final class StructuredAsyncScope<T, A, V> extends StructuredTaskScope<T> {
  @FunctionalInterface
  public interface Combiner<T, A> {
    A apply(A oldValue, Result<T> result, Runnable shouldShutdown);
  }

  public record Reducer<T, A, V>(Combiner<T, A> combiner, Function<? super A, ? extends V> finisher) {
    public Reducer {
      requireNonNull(combiner);
      requireNonNull(finisher);
    }

    public Reducer<T, A, V> dropExceptions() {
      return new Reducer<>((oldValue, result, shouldShutdown) -> combiner.apply(oldValue, new Result<>(result.state, result.element, null), shouldShutdown), finisher);
    }

    public static <T> Reducer<T, ?, List<Result<T>>> toList() {
      record Link<T> (Result<T> result, int size, Link<T> next) {
        static <T> Link<T> combine(Link<T> old, Result<T> result, Runnable shutdown) {
          return new Link<>(result, old == null ? 1 : old.size + 1, old);
        }
        static <T> List<Result<T>> finish(Link<T> link) {
          if (link == null) {
            return List.of();
          }
          @SuppressWarnings("unchecked")
          var array = (Result<T>[]) new Result<?>[link.size];
          var i = link.size;
          for(var l = link; l!= null; l = l.next) {
            array[--i] = l.result();
          }
          return List.of(array);
        }
      }
      return new Reducer<T, Link<T>, List<Result<T>>>(Link::combine, Link::finish);
    }

    private static Throwable mergeException(Throwable throwable, Throwable other) {
      if (throwable == null) {
        return other;
      }
      if (other == null) {
        return throwable;
      }
      throwable.addSuppressed(other);
      return throwable;
    }

    private static <T> Result<T> maxMergeResult(Result<T> result, Result<T> other, Comparator<? super T> comparator) {
      if (result == null) {
        return other;
      }
      var newException = mergeException(result.suppressed, other.suppressed);
      if (result.state == State.SUCCEED) {
        if (other.state == State.SUCCEED) {
          var e = result.element;
          var o = other.element;
          var newElement = comparator.compare(e, o) >= 0 ? e : o;
          return new Result<>(State.SUCCEED, newElement, newException);
        }
        return new Result<>(State.SUCCEED, result.element, newException);
      }
      return new Result<>(other.state, other.element, newException);
    }

    public static <T> Reducer<T, ?, Optional<Result<T>>> max(Comparator<? super T> comparator) {
      return new Reducer<T, Result<T>, Optional<Result<T>>>((r1, r2, shutdown) -> maxMergeResult(r1, r2, comparator), Optional::ofNullable);
    }

    private static <T> Result<T> firstMergeResult(Result<T> result, Result<T> other, Runnable shutdown) {
      if (result == null) {
        if (other.state == State.SUCCEED) {
          shutdown.run();
        }
        return other;
      }
      var newException = mergeException(result.suppressed, other.suppressed);
      if (result.state == State.SUCCEED) {
        return new Result<>(State.SUCCEED, result.element, newException);
      }
      if (other.state == State.SUCCEED) {
        shutdown.run();
      }
      return new Result<>(other.state, other.element, newException);
    }

    public static <T> Reducer<T, ?, Optional<Result<T>>> first() {
      return new Reducer<T, Result<T>, Optional<Result<T>>>(Reducer::firstMergeResult, Optional::ofNullable);
    }

    public Reducer<T, A, V> shutdownOnFailure() {
      return new Reducer<T, A, V>((oldValue, result, shouldShutdown) -> {
        if (result.state == State.FAILED) {
          shouldShutdown.run();
        }
        return combiner.apply(oldValue, result, shouldShutdown);
      }, finisher);
    }

    public static <T> Reducer<T, ?, Optional<Throwable>> firstException() {
      return new Reducer<T, Result<T>, Optional<Throwable>>((oldValue, result, shouldShutdown) -> {
        if (oldValue != null && oldValue.state == State.FAILED) {
          return oldValue;
        }
        return result;
      }, result -> Optional.ofNullable(result.suppressed));
    }
  }

  public record Result<T>(State state, T element, Throwable suppressed) {
    public enum State {
      FAILED, SUCCEED
    }

    public Result {
      requireNonNull(state);
      if (state == State.FAILED && element != null) {
        throw new IllegalArgumentException("if state == FAILED, element should be null");
      }
    }
  }

  private static final VarHandle VALUE_HANDLE;
  static {
    try {
      VALUE_HANDLE = MethodHandles.lookup().findVarHandle(StructuredAsyncScope.class, "value", Object.class);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }

  private volatile A value;
  private final Reducer<T, A, V> reducer;

  public StructuredAsyncScope(Reducer<T, A, V> reducer) {
    this.reducer = requireNonNull(reducer);
  }

  public static <T, V> StructuredAsyncScope<T, ?, V> of(Reducer<T, ?, V> reducer) {
    return new StructuredAsyncScope<>(reducer);
  }

  @Override
  protected void handleComplete(Future<T> future) {
    Result<T> result;
    switch (future.state()) {
      case CANCELLED -> {
        // automatic cancelling of the other tasks
        // this is a no-op if the scope is already shutdown
        shutdown();
        return;
      }
      case SUCCESS -> result = new Result<>(State.SUCCEED, future.resultNow(), null);
      case FAILED -> result = new Result<>(State.FAILED, null, future.exceptionNow());
      default -> throw new AssertionError();
    }
    var shouldShutdown = new Runnable() {
      private boolean shutdown;
      @Override
      public void run() {
        shutdown = true;
      }
    };
    for(;;) {
      var oldValue = value;  // volatile read
      var newValue = reducer.combiner.apply(oldValue, result, shouldShutdown);
      if (VALUE_HANDLE.compareAndSet(this, oldValue, newValue)) {  // volatile read/write
        if (shouldShutdown.shutdown) {
          shutdown();
        }
        return;
      }
    }
  }

  public V result() throws InterruptedException {
    join();
    return reducer.finisher.apply(value);  // volatile read
  }

  public V result(Instant deadline) throws InterruptedException, TimeoutException {
    joinUntil(deadline);
    return reducer.finisher.apply(value);  // volatile read
  }
}
