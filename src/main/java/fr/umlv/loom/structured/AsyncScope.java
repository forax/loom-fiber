package fr.umlv.loom.structured;

import jdk.incubator.concurrent.StructuredTaskScope;

import java.util.Objects;
import java.util.Spliterator;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class AsyncScope<T, E extends Exception> implements AutoCloseable {
  /**
   * A callable that propagates the checked exceptions
   * @param <T> type of the result
   * @param <E> type of the checked exception, uses {@code RuntimeException} otherwise.
   */
  @FunctionalInterface
  public interface Computation<T, E extends Exception> {
    /**
     * Compute the computation.
     * @return a result
     * @throws E an exception
     * @throws InterruptedException if the computation is interrupted or cancelled
     */
    T compute() throws E, InterruptedException;
  }

  /**
   * Result of an asynchronous computation
   *
   * @param <T> type of the result of the computation
   * @param <E> type of the exception thrown by the computation
   */
  public interface TaskHandle<T, E extends Exception> {
    enum State {
      RUNNING, SUCCESS, FAILED, CANCELLED
    }

    /**
     * Returns the state of the task.
     * @return
     */
    State state();

    /**
     * Returns the exception, if the task failed with an exception.
     * @return
     */
    E exception();

    /**
     * Returns the result, if the task completed successfully.
     * @return
     */
    T get();

    /**
     * Returns the value of the computation
     * @return the value of the computation
     * @throws E the exception thrown by the computation
     * @throws CancelledException if the task was cancelled.
     * @throws IllegalStateException if the computation is not done.
     */
    T getNow() throws E, CancelledException;
  }

  public static final class CancelledException extends RuntimeException {
    private CancelledException() {
      super(null, null, false, false);
    }
  }

  /**
   * Result of a computation.
   *
   * @param <T> type of the result value
   * @param <E> type of the exception in case of failure
   */
  public static final class Result<T, E extends Exception> {
    public enum State {
      /**
       * if the computation succeed.
       */
      SUCCESS,
      /**
       * If the computation failed because an exception is thrown
       */
      FAILED
    }

    private final State state;
    private final T result;
    private final E failure;

    private Result(State state, T result, E failure) {
      this.state = state;
      this.result = result;
      this.failure = failure;
    }

    /**
     * Returns the state of the result.
     * @return the state of the result.
     */
    public State state() {
      return state;
    }

    /**
     * Returns the result of the computation.
     * @throws IllegalStateException if the state is not {@link State#SUCCESS}.
     * @return the result of the computation.
     */
    public T result() {
      if (state != State.SUCCESS) {
        throw new IllegalStateException("state not a success");
      }
      return result;
    }

    /**
     * Returns the failure thrown by the computation.
     * @throws IllegalStateException if the state is not {@link State#FAILED}.
     * @return the failure thrown by the computation.
     */
    public E failure() {
      if (state != State.FAILED) {
        throw new IllegalStateException("state not a failure");
      }
      return failure;
    }

    /**
     * Returns the value of the computation
     * @return the value of the computation
     * @throws E the exception thrown by the computation
     */
    public T getNow() throws E {
      return switch (state) {
        case SUCCESS -> result;
        case FAILED -> throw failure;
      };
    }

    public boolean isSuccess() {
      return state == State.SUCCESS;
    }

    public boolean isFailed() {
      return state == State.FAILED;
    }

    /**
     * Returns either an empty stream either if the computation failed
     * or if the computation succeed a stream with one value, the result of the computation.
     * @return either an empty stream if the computation failed or a stream with the result of the computation.
     */
    public Stream<T> keepOnlySuccess() {
      return switch (state) {
        case SUCCESS -> Stream.of(result);
        case FAILED -> Stream.empty();
      };
    }

    /**
     * Returns a binary function to {@link Stream#reduce(BinaryOperator)} two results.
     * If the two results are both success, the success merger is called, if the two results
     * are both failures the first one is returned, the second exception is added as
     * {@link Throwable#addSuppressed(Throwable) suppressed exception}.
     * If the two results does not have the same type, a success is preferred to a failure.
     *
     * @param successMerger a binary function to merge to results
     * @return a binary function to {@link Stream#reduce(BinaryOperator)} two results.
     * @param <R> type of the result value
     * @param <E> type of the result exception
     */
    public static <R, E extends Exception> BinaryOperator<Result<R,E>> merger(BinaryOperator<R> successMerger) {
      Objects.requireNonNull(successMerger, "successMerger is null");
      return (result1, result2) -> switch (result1.state) {
        case SUCCESS -> switch (result2.state) {
          case SUCCESS -> new Result<>(State.SUCCESS, successMerger.apply(result1.result, result2.result), null);
          case FAILED -> result1;
        };
        case FAILED -> switch (result2.state) {
          case SUCCESS -> result2;
          case FAILED -> {
            result1.failure.addSuppressed(result2.failure);
            yield result1;
          }
        };
      };
    }

    /**
     * Returns a collector that collect the successful results using a downstream collector or
     * if all results have failed keep the first failure and adds the other failure as suppressed exceptions.
     *
     * @param downstream a downstream collector
     * @return a collector that collect the successful results using a downstream collector
     *
     * @param <R> type of successful result
     * @param <E> type of failure exception
     * @param <A> type of the intermediary value of the downstream collector
     * @param <D> type of the final value of the downstream collector
     */
    public static <R, E extends Exception,A,D> Collector<Result<R, E>, ?, Result<D, E>> toResult(Collector<? super R, A, D> downstream) {
      Objects.requireNonNull(downstream, "downstream collector is null");
      var downstreamSupplier = downstream.supplier();
      var downstreamAccumulator = downstream.accumulator();
      //var downstreamCombiner =  downstream.combiner();
      var downstreamFinisher = downstream.finisher();
      class Box {  // Collector API is mutable
        private Result<A,E> value;
      }
      return Collector.of(
          Box::new,
          (box, result) -> {
            if (box.value == null) {  // not initialized yet !
              switch (result.state) {
                case SUCCESS -> {
                  var a = downstreamSupplier.get();
                  downstreamAccumulator.accept(a, result.result);
                  box.value = new Result<>(State.SUCCESS, a, null);
                }
                case FAILED -> {
                  box.value = (Result<A, E>) result;
                }
              }
              return;
            }
            switch(result.state) {
              case SUCCESS -> {
                switch (box.value.state) {
                  case SUCCESS -> downstreamAccumulator.accept(box.value.result, result.result);
                  case FAILED -> {
                    var a = downstreamSupplier.get();
                    downstreamAccumulator.accept(a, result.result);
                    box.value = new Result<>(State.SUCCESS, a, null);
                  }
                }
              }
              case FAILED -> {
                switch (box.value.state) {
                  case SUCCESS -> {}
                  case FAILED -> box.value.failure.addSuppressed(result.failure);
                }
              }
            }
          },
          (box1, box2) -> {
            throw new IllegalStateException("this collector does not support parallel streams");
          },
          box -> {
            if (box.value == null) {  // not initialized
              return new Result<>(State.SUCCESS, downstreamFinisher.apply(downstreamSupplier.get()), null);
            }
            return switch (box.value.state) {
              case FAILED -> (Result<D, E>) box.value;
              case SUCCESS -> new Result<>(State.SUCCESS, downstreamFinisher.apply(box.value.result), null);
            };
          }
      );
    }
  }

  private final Thread ownerThread;
  private final StructuredTaskScope<T> taskScope;
  private final LinkedBlockingQueue<Future<T>> futures = new LinkedBlockingQueue<>();
  private int tasks;

  /**
   * Creates an asynchronous scope to manage several asynchronous computations.
   */
  public AsyncScope() {
    this.ownerThread = Thread.currentThread();
    this.taskScope = new StructuredTaskScope<>() {
      @Override
      protected void handleComplete(Future<T> future) {
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
   * @see TaskHandle#getNow()
   */
  public TaskHandle<T, E> async(Computation<? extends T, ? extends E> computation) {
    var future = taskScope.<T>fork(computation::compute);
    tasks++;
    return new TaskHandle<>() {
      @Override
      public State state() {
        return switch (future.state()) {
          case RUNNING -> State.RUNNING;
          case SUCCESS -> State.SUCCESS;
          case FAILED -> {
            if (future.exceptionNow() instanceof InterruptedException) {
              yield State.CANCELLED;
            }
            yield State.FAILED;
          }
          case CANCELLED -> State.CANCELLED;
        };
      }

      @Override
      public E exception() {
        if (future.state() != Future.State.FAILED) {
          throw new IllegalStateException();
        }
        var exception = future.exceptionNow();
        if (exception instanceof InterruptedException) {
          throw new IllegalStateException();
        }
        return (E) exception;
      }

      @Override
      public T get() {
        if (future.state() != Future.State.SUCCESS) {
          throw new IllegalStateException();
        }
        return (T) future.resultNow();
      }

      @Override
      public T getNow() throws E, CancelledException {
        if (!future.isDone()) {
          throw new IllegalStateException("Task is not completed");
        }
        return switch (future.state()) {
          case RUNNING -> throw new AssertionError();
          case SUCCESS -> future.resultNow();
          case CANCELLED -> throw new CancelledException();
          case FAILED -> {
            var throwable = future.exceptionNow();
            if (throwable instanceof InterruptedException) {
              throw new CancelledException();
            }
            throw (E) throwable;
          }
        };
      }
    };
  }

  private Result<T, E> toResult(Future<T> future) {
    return switch (future.state()) {
      case RUNNING, CANCELLED -> throw new AssertionError();
      case SUCCESS -> new Result<>(Result.State.SUCCESS, future.resultNow(), null);
      case FAILED -> {
        var throwable = future.exceptionNow();
        if (throwable instanceof InterruptedException) {
          yield null;
        }
        yield new Result<>(Result.State.FAILED, null, (E) throwable);
      }
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

  private final class ResultSpliterator implements Spliterator<Result<T,E>> {
    @Override
    public boolean tryAdvance(Consumer<? super Result<T, E>> action) {
      checkThread();
      if (tasks == 0) {
        return false;
      }
      Future<T> future;
      try {
        future = futures.take();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        tasks = 0;
        return false;
      }
      var result = toResult(future);
      if (result != null) {
        action.accept(result);
      }
      tasks--;
      return true;
    }

    @Override
    public Spliterator<Result<T, E>> trySplit() {
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
   * If the stream sent to the stream mapper is short-circuited then the non-finished tasks will be cancelled.
   *
   * @param streamMapper a function that takes a stream of results and transform it to a value.
   * @return the result the stream mapper function.
   * @param <V> the type of the result of the stream mapper function
   * @throws InterruptedException if the current thread is interrupted
   * @throws WrongThreadException if this method is not called by the thread that has created this scope.
   */
  public <V> V await(Function<? super Stream<Result<T,E>>, ? extends V> streamMapper) throws InterruptedException {
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
