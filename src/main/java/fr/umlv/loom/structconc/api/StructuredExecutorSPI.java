package fr.umlv.loom.structconc.api;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.StructuredExecutor;
import java.util.function.Consumer;

public class StructuredExecutorSPI {
  public static <V> Future<V> fork(StructuredExecutor executor, Callable<? extends V> callable, Consumer<? super Future<V>> completerConsumer) {
    return executor.fork((Callable<V>) callable, (__, future) -> completerConsumer.accept(future));
  }
}
