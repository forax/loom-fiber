package fr.umlv.loom.continuation;

import java.util.ArrayList;
import java.util.Objects;
import java.util.stream.IntStream;

public class SemaphoreExample {  
  public static class Semaphore {
    private int permits;
    private final Scheduler scheduler;
    private final ArrayList<Continuation> waitQueue;
    
    public Semaphore(int permits, Scheduler scheduler) {
      if (permits <= 0) {
        throw new IllegalArgumentException();
      }
      this.permits = permits;
      this.scheduler = Objects.requireNonNull(scheduler);
      this.waitQueue = new ArrayList<>();
    }

    public void acquire(int somePermits) {
      for(;;) {
        if (permits >= somePermits) {
          permits -= somePermits;
          return;
        }
        var continuation = Scheduler.currentContinuation();
        waitQueue.add(continuation);
        scheduler.yield();
      }
    }
    
    public void release(int somePermits) {
      permits += somePermits;
      waitQueue.forEach(scheduler::register);
      waitQueue.clear();
    }
  }
  
  public static void main(String[] args) {
    //var scheduler = new FifoScheduler();
    var scheduler = new RandomScheduler();
    var semaphore = new Semaphore(5, scheduler);
    IntStream.rangeClosed(1, 5).forEach(id -> {
      scheduler.schedule(() -> {
        for(;;) {
          System.out.println("try acquire " + id);
          semaphore.acquire(id);
          System.out.println("acquired " + id);

          scheduler.pause();

          System.out.println("try release " + id);
          semaphore.release(id);
          System.out.println("released " + id);
          
          scheduler.pause();
        }
      });
    });
    scheduler.loop();
  }
}
