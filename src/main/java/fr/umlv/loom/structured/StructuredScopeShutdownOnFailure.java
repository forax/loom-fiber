package fr.umlv.loom.structured;

import java.util.Objects;
import java.util.concurrent.StructuredTaskScope;
import java.util.function.Function;
import java.util.function.Supplier;

public class StructuredScopeShutdownOnFailure<E extends Exception> implements AutoCloseable {
  private final StructuredTaskScope.ShutdownOnFailure scope;

  public StructuredScopeShutdownOnFailure() {
    this.scope = new StructuredTaskScope.ShutdownOnFailure();
  }

  public interface Subtask<T> extends Supplier<T> {
    enum State { SUCCESS, FAILED, UNAVAILABLE }

    State state();

    T get();
  }

  public <T> Subtask<T> fork(Invokable<? extends T, ? extends E> invokable) {
    var subtask = scope.fork(invokable::invoke);
    return new Subtask<T>() {
      @Override
      public State state() {
        return switch (subtask.state()) {
          case UNAVAILABLE -> State.UNAVAILABLE;
          case SUCCESS -> State.SUCCESS;
          case FAILED -> {
            var throwable = subtask.exception();
            if (throwable instanceof InterruptedException) {
              yield State.UNAVAILABLE;
            }
            yield State.FAILED;
          }
        };
      }

      @Override
      public T get() {
        return switch (subtask.state()) {
          case UNAVAILABLE, FAILED -> throw new IllegalStateException();
          case SUCCESS -> subtask.get();
        };
      }
    };
  }

  public void joinAll() throws E, InterruptedException {
    joinAll(e -> e);
  }

  public <X extends Exception> void joinAll(Function<? super E, ? extends X> exceptionMapper) throws X, InterruptedException {
    Objects.requireNonNull(exceptionMapper, "exceptionMapper is null");
    scope.join();
    scope.throwIfFailed(throwable -> {
      if (throwable instanceof RuntimeException e) {
        throw e;
      }
      if (throwable instanceof Error e) {
        throw e;
      }
      if (throwable instanceof InterruptedException e) {
        return (X) e;  // dubious cast
      }
      return exceptionMapper.apply((E) throwable);
    });
  }

  @Override
  public void close() {
    scope.close();
  }
}
