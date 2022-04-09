package fr.umlv.loom.monad;

import fr.umlv.loom.monad.AsyncScope.ExceptionHandler;  // not used !

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class OldAsyncStreams {
  /**
   * Exception thrown if the {@link #asyncWithDeadline(Stream, Instant, Computation)} is reached.
   */
  public static final class DeadlineException extends RuntimeException {
    /**
     * DeadlineException with a message.
     * @param message the message of the exception
     */
    public DeadlineException(String message) {
      super(message);
    }

    /**
     * DeadlineException with a message and a cause.
     * @param message the message of the exception
     * @param cause the cause of the exception
     */
    public DeadlineException(String message, Throwable cause) {
      super(message, cause);
    }

    /**
     * DeadlineException with a cause.
     * @param cause the cause of the exception
     */
    public DeadlineException(Throwable cause) {
      super(cause);
    }
  }

  public interface Computation<V, R, E extends Exception> {
    R compute(V value) throws E, InterruptedException;
  }

  public static <V, R, E extends Exception> Stream<R> async(Stream<? extends V> values, Computation<? super V, ? extends R, ? extends E> computation) throws E, InterruptedException {
    return asyncWithDeadline(values, null, computation);
  }

  public static <V, R, E extends Exception> Stream<R> asyncWithDeadline(Stream<? extends V> values, Instant deadline, Computation<? super V, ? extends R, ? extends E> computation) throws E, InterruptedException {
    Objects.requireNonNull(values);
    Objects.requireNonNull(computation);
    var executorService = Executors.newVirtualThreadPerTaskExecutor();
    var valueSpliterator = values.spliterator();
    if (!valueSpliterator.hasCharacteristics(Spliterator.SIZED) && valueSpliterator.estimateSize() == Long.MAX_VALUE) {
      throw new IllegalStateException("infinite stream are not supported");
    }
    var ordered = valueSpliterator.hasCharacteristics(Spliterator.ORDERED);
    var completionService = ordered? null: new ExecutorCompletionService<R>(executorService);
    var futures = new ArrayList<Future<R>>();
    valueSpliterator.forEachRemaining(value -> futures.add(completionService != null?
          completionService.submit(() ->  computation.compute(value)):
          executorService.submit(() -> computation.compute(value))));
    var spliterator = ordered?
        orderedSpliterator(executorService, futures, null, deadline):
        unorderedSpliterator(executorService, completionService, futures, null, deadline);
    return StreamSupport.stream(spliterator, false).
        onClose(() -> {
          executorService.shutdownNow();
          try {
            while(!executorService.awaitTermination(1, TimeUnit.DAYS)) {
              // empty
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });
  }

  // Utilities
  // this code is duplicated in AsyncMonadImpl and AsyncScopeImpl

  private static <R, E extends Exception> Spliterator<R> orderedSpliterator(ExecutorService executorService, List<Future<R>> futures, ExceptionHandler<Exception, ? extends R, ? extends E> handler, Instant deadline) {
    return new Spliterator<>() {
      private int index;

      @Override
      public boolean tryAdvance(Consumer<? super R> action) {
        if (index == futures.size()) {
          return false;
        }
        var future = futures.get(index);
        try {
          R result;
          try {
            result = deadline == null?
                future.get():
                future.get(timeoutInNanos(deadline), TimeUnit.NANOSECONDS);
          } catch (ExecutionException e) {
            result = handleException(e.getCause(), handler);
          } catch (TimeoutException e) {
            throw new DeadlineException("deadline reached", e);
          }
          action.accept(result);
        } catch (Exception e) {
          executorService.shutdownNow();
          throw rethrow(e);
        }
        index++;
        return true;
      }

      @Override
      public Spliterator<R> trySplit() {
        return null;
      }
      @Override
      public long estimateSize() {
        return futures.size() - index;
      }
      @Override
      public int characteristics() {
        return IMMUTABLE | SIZED | ORDERED;
      }
    };
  }

  private static <R, E extends Exception> Spliterator<R> unorderedSpliterator(ExecutorService executorService, ExecutorCompletionService<R> completionService, List<Future<R>> futures, ExceptionHandler<Exception, ? extends R, ? extends E> handler, Instant deadline) {
    return new Spliterator<>() {
      private int index;

      @Override
      public boolean tryAdvance(Consumer<? super R> action) {
        if (index == futures.size()) {
          return false;
        }
        try {
          var future = deadline == null?
              completionService.take():
              completionService.poll(timeoutInNanos(deadline), TimeUnit.NANOSECONDS);
          if (future == null) {
            throw new DeadlineException("deadline reached");
          }
          R result;
          try {
            result = future.get();
          } catch (ExecutionException e) {
            result = handleException(e.getCause(), handler);
          }
          action.accept(result);
          index++;
          return true;
        } catch (Exception e) {
          executorService.shutdownNow();
          throw rethrow(e);
        }
      }

      @Override
      public Spliterator<R> trySplit() {
        return null;
      }
      @Override
      public long estimateSize() {
        return futures.size() - index;
      }
      @Override
      public int characteristics() {
        return IMMUTABLE | SIZED;
      }
    };
  }

  private static long timeoutInNanos(Instant deadline) {
    var timeout = Duration.between(Instant.now(), deadline).toNanos();
    return timeout < 0? 0: timeout;
  }

  private static <R, E extends Exception> R handleException(Throwable cause, ExceptionHandler<Exception, ? extends R, ? extends E> handler) throws InterruptedException {
    if (cause instanceof RuntimeException e) {
      throw e;
    }
    if (cause instanceof Error e) {
      throw e;
    }
    if (cause instanceof InterruptedException e) {
      throw e;
    }
    if (cause instanceof Exception e) {
      if (handler != null) {
        R newValue;
        try {
          newValue = handler.handle(e);
        } catch (Exception newException) {
          throw rethrow(newException);
        }
        return newValue;
      }
    }
    throw rethrow(cause);
  }

  @SuppressWarnings("unchecked")   // very wrong but works
  private static <T extends Throwable> AssertionError rethrow(Throwable cause) throws T {
    throw (T) cause;
  }
}
