package fr.umlv.loom.scheduler;

import java.util.ArrayDeque;
import java.util.Objects;

public class SimpleScheduler {
  final ContinuationScope scope;
  final ArrayDeque<Continuation> schedulable = new ArrayDeque<>();
  
  public SimpleScheduler() {
    scope = new ContinuationScope("scheduler-" + Integer.toHexString(System.identityHashCode(this)));
  }
  
  Continuation currentContinuation() {
    var currentContinuation = Continuation.getCurrentContinuation(scope);
    if (currentContinuation == null) {
      throw new IllegalStateException("no current continuation");
    }
    return currentContinuation;
  }
  
  public void execute(Runnable runnable) {
    var continuation = new Continuation(scope, runnable);
    schedulable.add(continuation);
    
    var currentContinuation = Continuation.getCurrentContinuation(scope);
    if (currentContinuation == null) {
      loop();
    }
  }
  
  void loop() {
    while(!schedulable.isEmpty()) {
      Continuation continuation = schedulable.poll();
      continuation.run();
    }
  }
  
  static class Exchanger<V> {
    private final SimpleScheduler scheduler;
    private V value;
    private Continuation continuation;
    
    public Exchanger(SimpleScheduler scheduler) {
      this.scheduler = Objects.requireNonNull(scheduler);
    }

    public V exchange(V value) {
      Objects.requireNonNull(value);
      var continuation = scheduler.currentContinuation();
      if (this.value == null) {
        this.value = value;
        this.continuation = continuation;
        Continuation.yield(scheduler.scope);
        var result = this.value;
        this.value = null;
        return result;
      }
      var result = this.value;
      this.value = value;
      scheduler.schedulable.add(this.continuation);
      this.continuation = null;
      return result;
    }
  }
  
  public static void main(String[] args) {
    var scheduler = new SimpleScheduler();
    var exchanger = new Exchanger<String>(scheduler);
    scheduler.execute(() -> {
      System.out.println("cont1: " + exchanger.exchange("hello"));
    });
    scheduler.execute(() -> {
      System.out.println("cont2: " + exchanger.exchange("hi"));
    });
  }
}
