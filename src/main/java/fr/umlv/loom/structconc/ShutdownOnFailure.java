package fr.umlv.loom.structconc;

import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.StructuredExecutor;
import java.util.concurrent.TimeoutException;

public class ShutdownOnFailure {
  private final StructuredExecutor executor;
  private final StructuredExecutor.ShutdownOnFailure shutdownOnFailure;

  public ShutdownOnFailure(StructuredExecutor executor) {
    this.executor = executor;
    this.shutdownOnFailure = new StructuredExecutor.ShutdownOnFailure();
  }

  public <V> Future<V> fork(Callable<V> task) {
    return StructuredExecutorSPI.fork(executor, task, future -> shutdownOnFailure.accept(executor, (Future<Object>) future));
  }

  public void join() throws InterruptedException {
    executor.join();
  }

  public void joinUntil(Instant deadline) throws InterruptedException, TimeoutException {
    executor.joinUntil(deadline);
  }
}