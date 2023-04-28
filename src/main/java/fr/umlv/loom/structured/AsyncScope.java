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
  @FunctionalInterface
  public interface Computation<R, E extends Exception> {
    R compute() throws E, InterruptedException;
  }

  public interface AsyncTask<R, E extends Exception> extends Future<R> {
    Result<R, E> result();

    R getNow() throws E, InterruptedException;
  }

  public sealed interface Result<R, E extends Exception> {
    default Stream<R> withOnlySuccess() {
      return switch (this) {
        case Success<?> success -> Stream.of((R) success.result);
        case Canceled canceled -> Stream.empty();
        case Failure<?> failure -> Stream.empty();
      };
    }

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
  public record Success<R>(R result) implements Result<R, Exception> {}
  public record Canceled() implements Result<Void, RuntimeException> {}
  public record Failure<E extends Exception>(E exception) implements Result<Void, E> {
    public Failure {
      Objects.requireNonNull(exception);
    }
  }

  private final Thread ownerThread;
  private final StructuredTaskScope<R> taskScope;
  private final LinkedBlockingQueue<Future<R>> futures = new LinkedBlockingQueue<>();
  private int tasks;

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
