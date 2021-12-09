package fr.umlv.loom.structconc.api;

import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.StructuredExecutor;
import java.util.concurrent.TimeoutException;

public class ShutdownOnSuccess<V, X extends Exception> {
  private final StructuredExecutor executor;
  private final StructuredExecutor.ShutdownOnSuccess<V> shutdownOnSuccess;

  public ShutdownOnSuccess(StructuredExecutor executor) {
    this.executor = executor;
    this.shutdownOnSuccess = new StructuredExecutor.ShutdownOnSuccess<>();
  }

  public Future<V> fork(CallableWithException<V, X> task) {
    return StructuredExecutorSPI.fork(executor, task::call, future -> shutdownOnSuccess.handle(executor, future));
  }

  public V race() throws InterruptedException, X {
    executor.join();
    try {
      return shutdownOnSuccess.result();
    } catch (ExecutionException e) {
      var cause = e.getCause();
      if(cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw (X) cause;
    }
  }

  public V raceUntil(Instant deadline) throws InterruptedException, TimeoutException, X {
    executor.joinUntil(deadline);
    try {
      return shutdownOnSuccess.result();
    } catch (ExecutionException e) {
      var cause = e.getCause();
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw (X) cause;
    }
  }
}
