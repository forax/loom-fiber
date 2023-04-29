package fr.umlv.loom.structured;

import jdk.incubator.concurrent.StructuredTaskScope;

import java.util.Objects;
import java.util.Spliterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class AsyncScope<R, E extends Exception> implements AutoCloseable {
  /**
   * A callable that propagates the checked exceptions
   * @param <R> type of the result
   * @param <E> type of the checked exception, uses RuntimeException otherwise.
   */
  @FunctionalInterface
  public interface Computation<R, E extends Exception> {
    /**
     * Compute the computation.
     * @return a result
     * @throws E an exception
     * @throws InterruptedException if the computation is interrupted
     */
    R compute() throws E, InterruptedException;
  }

  /**
   * Result of an asynchronous computation
   *
   * @param <R> type of the result of the computation
   * @param <E> type of the exception thrown by the computation
   */
  public interface AsyncTask<R, E extends Exception> extends Future<R> {
    /**
     * Returns a result object corresponding to the computation if the computation is done.
     * @return a result object corresponding to the computation if the computation is done.
     * @throws IllegalStateException if the computation is not done.
     *
     * @see #isDone()
     */
    Result<R, E> result();

    /**
     * Returns the value of the computation
     * @return the value of the computation
     * @throws E the exception thrown by the computation
     * @throws InterruptedException if the task was interrupted
     * @throws IllegalStateException if the computation is not done.
     */
    R getNow() throws E, InterruptedException;
  }

  public sealed interface Result<R, E extends Exception> {
    /**
     * Returns the value of the computation
     * @return the value of the computation
     * @throws E the exception thrown by the computation
     * @throws InterruptedException if the task was interrupted
     */
    default R getNow() throws E, InterruptedException {
      return switch (this) {
        case Success<?> success -> (R) success.result;
        case Canceled canceled -> throw new InterruptedException();
        case Failure<?> failure -> throw (E) failure.exception;
      };
    }

    /**
     * Returns a stream either empty if the computation failed or was cancelled
     * or a stream with one value, the result of the computation.
     * @return a stream either empty if the computation failed or was cancelled
     *         or a stream with one value, the result of the computation.
     */
    default Stream<R> keepOnlySuccess() {
      return switch (this) {
        case Success<?> success -> Stream.of((R) success.result);
        case Canceled canceled -> Stream.empty();
        case Failure<?> failure -> Stream.empty();
      };
    }

    /**
     * Returns a binary function to {@link Stream#reduce(BinaryOperator)} two results.
     * If the two results are both success, the success merger is called, if the two results
     * are both failures the first one is returned, the second exception is added as
     * {@link Throwable#addSuppressed(Throwable) suppressed exception}.
     * If the two results does not have the same type, a success is preferred to a failure,
     * a failure is preferred to a cancellation.
     *
     * @param successMerger a binary function to merge to results
     * @return a binary function to {@link Stream#reduce(BinaryOperator)} two results.
     * @param <R> type of the result value
     * @param <E> type of the result exception
     */
    static <R, E extends Exception> BinaryOperator<Result<R,E>> merger(BinaryOperator<R> successMerger) {
      return (result1, result2) -> switch (result1) {
        case Canceled canceled1 -> result2;
        case Success<?> success1 -> (Result<R,E>) switch (result2) {
          case Canceled canceled2 -> success1;
          case Failure<?> failure -> success1;
          case Success<?> success2 -> new Success<>(successMerger.apply((R) success1.result, (R) success2.result));
        };
        case Failure<?> failure -> (Result<R,E>) switch (result2) {
          case Canceled canceled2 -> failure;
          case Failure<?> failure2 -> {
            failure.exception.addSuppressed(failure2.exception);
            yield failure;
          }
          case Success<?> success2 -> success2;
        };
      };
    }
  }

  /**
   * A success of the {@link Computation}.
   *
   * @param result the resulting value of the computation.
   *        {@code null} is allowed.
   * @param <R> the type of the resulting value.
   */
  public record Success<R>(R result) implements Result<R, Exception> {}

  /**
   * A cancellation of the {@link Computation}.
   */
  public record Canceled() implements Result<Void, RuntimeException> {}

  /**
   * A failure of the {@link Computation}.
   *
   * @param exception the exception thrown by the computation
   * @param <E> the type of the exception thrown by the computation
   */
  public record Failure<E extends Exception>(E exception) implements Result<Void, E> {
    public Failure {
      Objects.requireNonNull(exception);
    }
  }

  private final Thread ownerThread;
  private final StructuredTaskScope<R> taskScope;
  private final LinkedBlockingQueue<Future<R>> futures = new LinkedBlockingQueue<>();
  private int tasks;

  /**
   * Creates an asynchronous scope to manage several asynchronous computations.
   */
  public AsyncScope() {
    this.ownerThread = Thread.currentThread();
    this.taskScope = new StructuredTaskScope<>() {
      @Override
      protected void handleComplete(Future<R> future) {
        futures.add(future);
      }
    };
  }

  private void checkThread() {
    if (ownerThread != Thread.currentThread()) {
      throw new WrongThreadException();
    }
  }

  @Override
  public void close() {
    taskScope.close();
  }

  /**
   * Starts an asynchronous computation on a new virtual thread.
   * @param computation the computation to run.
   * @return an asynchronous task, an object that represents the result of the computation in the future.
   *
   * @see AsyncTask#result()
   */
  public AsyncTask<R, E> async(Computation<? extends R, ? extends E> computation) {
    var future = taskScope.<R>fork(computation::compute);
    tasks++;
    return new AsyncTask<R, E>() {
      @Override
      public boolean cancel(boolean mayInterruptIfRunning) {
        throw new UnsupportedOperationException();
      }

      @Override
      public boolean isCancelled() {
        // FIXME why future.isCancelled() does not work here ?
        return future.state() == State.CANCELLED;
      }

      @Override
      public boolean isDone() {
        return future.isDone();
      }

      @Override
      public R get() throws InterruptedException, ExecutionException {
        return future.get();
      }

      @Override
      public R get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(timeout, unit);
      }

      @Override
      public Result<R, E> result() {
        if (!future.isDone()) {
          throw new IllegalStateException("Task has not completed");
        }
        return toResult(future);
      }

      @Override
      public R getNow() throws E, InterruptedException {
        if (!future.isDone()) {
          throw new IllegalStateException("Task has not completed");
        }
        return switch (future.state()) {
          case RUNNING -> throw new AssertionError();
          case SUCCESS -> future.resultNow();
          case FAILED -> throw (E) future.exceptionNow();
          case CANCELLED -> throw new InterruptedException();
        };
      }
    };
  }

  private Result<R, E> toResult(Future<R> future) {
    return switch (future.state()) {
      case RUNNING -> throw new AssertionError();
      case SUCCESS -> (Result<R,E>) new Success<R>(future.resultNow());
      case FAILED -> (Result<R,E>) new Failure<E>((E) future.exceptionNow());
      case CANCELLED -> (Result<R,E>) new Canceled();
    };
  }

  /**
   * Awaits for all synchronous computations started with {@link #async(Computation)} to finish.
   * @throws InterruptedException if the current thread is interrupted
   * @throws WrongThreadException if this method is not called by the thread that has created this scope.
   */
  public void awaitAll() throws InterruptedException {
    checkThread();
    taskScope.join();
    taskScope.shutdown();
  }

  private final class ResultSpliterator implements Spliterator<Result<R,E>> {
    @Override
    public boolean tryAdvance(Consumer<? super Result<R, E>> action) {
      checkThread();
      if (tasks == 0) {
        return false;
      }
      Future<R> future;
      try {
        future = futures.take();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
      action.accept(toResult(future));
      tasks--;
      return true;
    }

    @Override
    public Spliterator<Result<R, E>> trySplit() {
      return null;
    }

    @Override
    public long estimateSize() {
      checkThread();
      return tasks;
    }

    @Override
    public int characteristics() {
      return NONNULL | SIZED;
    }
  }

  /**
   * Awaits until the stream of {@link Result results} finished.
   * @param streamMapper a function that takes a stream of results and transform it to a value.
   * @return the result the stream mapper function.
   * @param <V> the type of the result of the stream mapper function
   * @throws InterruptedException if the current thread is interrupted
   * @throws WrongThreadException if this method is not called by the thread that has created this scope.
   */
  public <V> V await(Function<? super Stream<Result<R,E>>, ? extends V> streamMapper) throws InterruptedException {
    checkThread();
    var stream = StreamSupport.stream(new ResultSpliterator(), false);
    var value = streamMapper.apply(stream);
    if (Thread.interrupted()) {
      throw new InterruptedException();
    }
    taskScope.shutdown();
    taskScope.join();
    return value;
  }
}
