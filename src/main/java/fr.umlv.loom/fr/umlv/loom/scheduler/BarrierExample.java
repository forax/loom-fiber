package fr.umlv.loom.scheduler;

import java.util.ArrayList;
import java.util.Objects;
import java.util.stream.IntStream;

public class BarrierExample {  
  public static class Barrier {
    private final int party;
    private final Scheduler scheduler;
    private final ArrayList<Continuation> waitQueue;
    
    public Barrier(int party, Scheduler scheduler) {
      if (party <= 0) {
        throw new IllegalArgumentException();
      }
      this.party = party;
      this.scheduler = Objects.requireNonNull(scheduler);
      this.waitQueue = new ArrayList<>(party);
    }

    public void await() {
      var continuation = Scheduler.currentContinuation();
      waitQueue.add(continuation);
      if (waitQueue.size() == party) {
        waitQueue.forEach(scheduler::register);
        waitQueue.clear();
      }
      scheduler.yield();
    }
  }
  
  public static void main(String[] args) {
    var scheduler = new FifoScheduler();
    var barrier = new Barrier(5, scheduler);
    IntStream.range(0, 5).forEach(id -> {
      scheduler.schedule(() -> {
        System.out.println("wait " + id);
        barrier.await();
        System.out.println("released " + id);
      });
    });
    scheduler.loop();
  }
}
