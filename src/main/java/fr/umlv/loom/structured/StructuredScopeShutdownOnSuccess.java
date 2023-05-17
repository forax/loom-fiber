package fr.umlv.loom.structured;

import jdk.incubator.concurrent.StructuredTaskScope;

import java.util.function.Function;

public class StructuredScopeShutdownOnSuccess<T, E extends Exception> implements AutoCloseable {
  private final StructuredTaskScope.ShutdownOnSuccess<T> scope;

  public StructuredScopeShutdownOnSuccess() {
    this.scope = new StructuredTaskScope.ShutdownOnSuccess<T>();
  }

  public void fork(Invokable<? extends T, ? extends E> invokable) {
    scope.fork(invokable::invoke);
  }

  public T joinAll() throws E, InterruptedException, CancelledException {
    return joinAll(e -> e);
  }

  public <X extends Exception> T joinAll(Function<? super E, ? extends X> exceptionMapper) throws X, InterruptedException, CancelledException {
    scope.join();
    return scope.result(throwable -> {
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
