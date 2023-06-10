package fr.umlv.loom.structured;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.Spliterator;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.StructuredTaskScope;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class StructuredScopeAsStream<T, E extends Exception> implements AutoCloseable {

  /**
   * Result of an asynchronous computation
   *
   * @param <T> type of the result of the computation
   * @param <E> type of the exception thrown by the computation
   */
  public interface Subtask<T, E extends Exception> {
    enum State {
      SUCCESS, FAILED, UNAVAILABLE
    }

    /**
     * Returns the state of the task.
     * @return the state of the task.
     */
    State state();

    /**
     * Returns the value of the computation
     * @return the value of the computation
     * @throws E the exception thrown by the computation
     * @throws IllegalStateException if the computation is not done.
     */
    T get() throws E;
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
    public T get() throws E {
      return switch (state) {
        case SUCCESS -> result;
        case FAILED -> throw failure;
      };
    }

    @Override
    public String toString() {
      return switch (state) {
        case SUCCESS -> "Success(" + result + ")";
        case FAILED -> "Failed(" + failure + ")";
      };
    }

    public boolean isSuccess() {
      return state == State.SUCCESS;
    }

    public boolean isFailed() {
      return state == State.FAILED;
    }

    public <R> Result<R, E> mapResult(Function<? super T, ? extends R> resultMapper) {
      Objects.requireNonNull(resultMapper);
      return new Result<>(state, resultMapper.apply(result), failure);
    }

    public <X extends E> Result<T, X> mapException(Function<? super E, ? extends X> exceptionMapper) {
      Objects.requireNonNull(exceptionMapper);
      return new Result<>(state, result, exceptionMapper.apply(failure));
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
  private final LinkedBlockingQueue<Result<T,E>> tasks = new LinkedBlockingQueue<>();
  private volatile long taskCount;

  private static final VarHandle TASK_COUNT;
  static {
    try {
      TASK_COUNT = MethodHandles.lookup().findVarHandle(StructuredScopeAsStream.class, "taskCount", long.class);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }

  /**
   * Creates an asynchronous scope to manage several asynchronous computations.
   */
  public StructuredScopeAsStream() {
    this.ownerThread = Thread.currentThread();
    this.taskScope = new StructuredTaskScope<>() {
      @Override
      protected void handleComplete(Subtask<? extends T> subtask) {
        var result = toResult(subtask);
        if (result != null) {
          tasks.add(result);
        }
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
   * @param invokable the computation to run.
   * @return an asynchronous task, an object that represents the result of the computation in the future.
   *
   * @see Subtask#get()
   */
  public Subtask<T, E> fork(Invokable<? extends T, ? extends E> invokable) {
    var subtask = taskScope.<T>fork(invokable::invoke);
    TASK_COUNT.getAndAdd(this, 1);
    return new Subtask<>() {
      @Override
      public State state() {
        return switch (subtask.state()) {
          case SUCCESS -> State.SUCCESS;
          case FAILED -> {
            if (subtask.exception() instanceof InterruptedException) {
              yield State.UNAVAILABLE;
            }
            yield State.FAILED;
          }
          case UNAVAILABLE -> State.UNAVAILABLE;
        };
      }

      @Override
      public T get() throws E {
        return switch (subtask.state()) {
          case UNAVAILABLE -> throw new IllegalStateException("Task unavailable");
          case SUCCESS -> subtask.get();
          case FAILED -> {
            var throwable = subtask.exception();
            if (throwable instanceof InterruptedException) {
              throw new IllegalStateException("Task unavailable");
            }
            throw (E) throwable;
          }
        };
      }
    };
  }

  private Result<T, E> toResult(StructuredTaskScope.Subtask<? extends T> subtask) {
    return switch (subtask.state()) {
      case UNAVAILABLE -> throw new AssertionError();
      case SUCCESS -> new Result<>(Result.State.SUCCESS, subtask.get(), null);
      case FAILED -> {
        var throwable = subtask.exception();
        if (throwable instanceof InterruptedException) {
          yield null;
        }
        yield new Result<>(Result.State.FAILED, null, (E) throwable);
      }
    };
  }

  /**
   * Awaits for all synchronous computations started with {@link #fork(Invokable)} to finish.
   * @throws InterruptedException if the current thread is interrupted
   * @throws WrongThreadException if this method is not called by the thread that has created this scope.
   */
  public void joinAll() throws InterruptedException {
    checkThread();
    taskScope.join();
    taskScope.shutdown();
  }

  private final class ResultSpliterator implements Spliterator<Result<T,E>> {
    private long index;

    @Override
    public boolean tryAdvance(Consumer<? super Result<T, E>> action) {
      checkThread();
      if (index >= taskCount) {  // volatile read
        index = Long.MAX_VALUE;
        return false;
      }
      Result<T,E> result;
      try {
        result = tasks.take();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        index = Long.MAX_VALUE;
        return false;
      }
      action.accept(result);
      index++;
      return true;
    }

    @Override
    public Spliterator<Result<T, E>> trySplit() {
      return null;
    }

    @Override
    public long estimateSize() {
      checkThread();
      if (index == Long.MAX_VALUE) {
        return 0;
      }
      return taskCount - index;  // volatile read
    }

    @Override
    public int characteristics() {
      return NONNULL | SIZED | CONCURRENT;
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
  public <V> V joinAll(Function<? super Stream<Result<T,E>>, ? extends V> streamMapper) throws InterruptedException {
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

  /* useful overload ??
  public <R, X extends Exception> R joinAllToResult(Function<? super Stream<Result<T,E>>, ? extends Result<R,X>> streamMapper) throws X, InterruptedException {
    return joinAll(streamMapper).getNow();
  }*/
}
