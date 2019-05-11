package fr.umlv.loom.scheduler;

import static java.util.stream.IntStream.range;

import java.util.ArrayDeque;
import java.util.Objects;

public class WorkQueueExample {
  public static class Condition {
    private final Scheduler scheduler;
    private final ArrayDeque<Continuation> waitQueue = new ArrayDeque<>();
    
    public Condition(Scheduler scheduler) {
      this.scheduler = scheduler;
    }
    
    public void await() {
      var currentContinuation = Scheduler.currentContinuation();
      waitQueue.offer(currentContinuation);
      scheduler.yield();
    }
    
    public void signal() {
      Scheduler.currentContinuation();  // check that this a thread has a continuation
      var continuation = waitQueue.poll();
      if (continuation == null) {
        return;
      }
      scheduler.register(continuation);
    }
    
    public void signalAll() {
      Scheduler.currentContinuation();  // check that this a thread has a continuation
      waitQueue.forEach(scheduler::register);
      waitQueue.clear();
    }
  }
  
  public static class WorkQueue<T> {
    private final int capacity;
    private final ArrayDeque<T> queue;
    private final Condition isEmpty;
    private final Condition isFull;
    
    public WorkQueue(int capacity, Scheduler scheduler) {
      Objects.requireNonNull(scheduler);
      this.capacity = capacity;
      this.queue = new ArrayDeque<>(capacity);
      this.isEmpty = new Condition(scheduler);
      this.isFull = new Condition(scheduler);
    }
    
    public T take() {
      while (queue.isEmpty()) {
        isEmpty.await();
      }
      isFull.signalAll();
      return queue.pop();
    }
    
    public void put(T element) {
      while (queue.size() == capacity) {
        isFull.await();
      }
      isEmpty.signalAll();
      queue.offer(element);
    }
  }
  
  public static void main(String[] args) {
    var scheduler = new RandomScheduler();
    var workQueue = new WorkQueue<Integer>(4, scheduler);

    range(0, 10).forEach(id -> {
      scheduler.schedule(() -> {
        for(;;) {
          System.out.println(id + ": produce " + id);
          workQueue.put(id);
          scheduler.pause();
        }
      });
    });
    range(0, 10).forEach(id -> {
      scheduler.schedule(() -> {
        for(;;) {
          System.out.println(id + ": consume " + workQueue.take());
          scheduler.pause();
        }
      });
    });

    scheduler.loop();
  }
}
