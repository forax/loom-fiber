package fr.umlv.loom.structured;

import jdk.incubator.concurrent.StructuredTaskScope;

import java.util.function.Function;
import java.util.function.Supplier;

public class StructuredScopeShutdownOnFailure<E extends Exception> implements AutoCloseable {
  private final StructuredTaskScope.ShutdownOnFailure scope;

  public StructuredScopeShutdownOnFailure() {
    this.scope = new StructuredTaskScope.ShutdownOnFailure();
  }

  public interface TaskHandle<T> extends Supplier<T> {
    enum State { RUNNING, SUCCESS, FAILED, CANCELLED }

    State state();

    T get();
  }

  public <T> TaskHandle<T> fork(Invokable<? extends T, ? extends E> invokable) {
    var future = scope.fork(invokable::invoke);
    return new TaskHandle<T>() {
      @Override
      public State state() {
        return switch (future.state()) {
          case RUNNING -> State.RUNNING;
          case SUCCESS -> State.SUCCESS;
          case CANCELLED -> State.CANCELLED;
          case FAILED -> {
            var throwable = future.exceptionNow();
            if (throwable instanceof InterruptedException) {
              yield State.CANCELLED;
            }
            yield State.FAILED;
          }
        };
      }

      @Override
      public T get() {
        return switch (future.state()) {
          case RUNNING, CANCELLED, FAILED -> throw new IllegalStateException();
          case SUCCESS -> future.resultNow();
        };
      }
    };
  }

  public void joinAll() throws E, InterruptedException {
    joinAll(e -> e);
  }

  public <X extends Exception> void joinAll(Function<? super E, ? extends X> exceptionMapper) throws X, InterruptedException {
    scope.join();
    scope.throwIfFailed(throwable -> {
      if (throwable instanceof RuntimeException e) {
        throw e;
      }
      if (throwable instanceof Error e) {
        throw e;
      }
      if (throwable instanceof InterruptedException) {
        throw new CancelledException();
      }
      return exceptionMapper.apply((E) throwable);
    });
  }

  @Override
  public void close() {
    scope.close();
  }
}
