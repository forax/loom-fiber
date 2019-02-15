package fr.umlv.loom.scheduler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Objects;
import java.util.stream.IntStream;

public class BarrierScheduler {
  private static final ContinuationScope SCOPE = new ContinuationScope("SCHEDULER");
  private final ArrayDeque<Continuation> schedulable = new ArrayDeque<>();
  
  public void execute(Runnable runnable) {
    var continuation = new Continuation(SCOPE, runnable);
    schedulable.add(continuation);
    
    var currentContinuation = Continuation.getCurrentContinuation(SCOPE);
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
    private final int party;
    private final BarrierScheduler scheduler;
    private final ArrayList<Continuation> waitQueue;
    
    public Barrier(int party, BarrierScheduler scheduler) {
      if (party <= 0) {
        throw new IllegalArgumentException();
      }
      this.party = party;
      this.scheduler = Objects.requireNonNull(scheduler);
      this.waitQueue = new ArrayList<>(party);
    }

    public void await() {
      var continuation = Continuation.getCurrentContinuation(SCOPE);
      if (continuation == null) {
        throw new IllegalStateException("no current continuation");
      }
      waitQueue.add(continuation);
      if (waitQueue.size() == party) {
        scheduler.schedulable.addAll(waitQueue);
        waitQueue.clear();
      }
      Continuation.yield(SCOPE);
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
