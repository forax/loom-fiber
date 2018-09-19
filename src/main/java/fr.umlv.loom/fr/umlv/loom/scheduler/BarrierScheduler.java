package fr.umlv.loom.scheduler;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.stream.IntStream;

public class BarrierScheduler {
  final ContinuationScope scope;
  final ArrayDeque<Continuation> schedulable = new ArrayDeque<>();
  
  public BarrierScheduler() {
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
  
  static class Barrier {
    private final BarrierScheduler scheduler;
    private final int party;
    private final ArrayDeque<Continuation> waitQueue = new ArrayDeque<>();
    
    public Barrier(int party, BarrierScheduler scheduler) {
      if (party <= 0) {
        throw new IllegalArgumentException();
      }
      this.party = party;
      this.scheduler = Objects.requireNonNull(scheduler);
    }

    public void await() {
      Continuation continuation = scheduler.currentContinuation();
      waitQueue.add(continuation);
      if (waitQueue.size() == party) {
        scheduler.schedulable.addAll(waitQueue);
        waitQueue.clear();
      }
      Continuation.yield(scheduler.scope);
    }
  }
  
  public static void main(String[] args) {
    var scheduler = new BarrierScheduler();
    var barrier = new Barrier(5, scheduler);
    IntStream.range(0, 5).forEach(id -> {
      scheduler.execute(() -> {
        System.out.println("wait " + id);
        barrier.await();
        System.out.println("released " + id);
      });
    });
  }
}
