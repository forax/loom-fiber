package fr.umlv.loom.concstruct;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public interface ComputationGroup<V> extends AutoCloseable {
  public ComputationGroup<V> async(Callable<? extends V> task);
  
  public ComputationGroup<V> async(Executor scheduler, Callable<? extends V> task);
  
  public ComputationGroup<V> cancel();
  
  public ComputationGroup<V> join();
  
  @Override
  public default void close() {
    join();
  }
  
  public static <V> ComputationGroup<V> of() {
    return new ComputationGroup<>() {
      private final ArrayList<CompletableFuture<? extends V>> futures = new ArrayList<>();
      
      public ComputationGroup<V> async(Callable<? extends V> task) {
        var future = Fiber.schedule(task).toFuture();
        futures.add(future);
        return this;
      }
      
      public ComputationGroup<V> async(Executor scheduler, Callable<? extends V> task) {
        var future = Fiber.schedule(scheduler, task).toFuture();
        futures.add(future);
        return this;
      }
      
      public ComputationGroup<V> cancel() {
        for(var future: futures) {
          future.cancel(false);
        }
        return this;
      }
      
      public ComputationGroup<V> join() {
        futures.forEach(future -> future.join());
        return this;
      }
      
      @Override
      public void close() {
        join();
      }
    };
  }
}
