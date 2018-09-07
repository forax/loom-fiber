package fr.umlv.loom.scheduler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

public class Scheduler {
  final ContinuationScope scope;
  final ArrayList<Continuation> schedulable = new ArrayList<>();
  
  public Scheduler() {
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
  
  public void pause() {
    Continuation currentContinuation = currentContinuation();
    schedulable.add(currentContinuation);
    Continuation.yield(scope);
  }
  
  void loop() {
    var random = ThreadLocalRandom.current();
    while(!schedulable.isEmpty()) {
      var continuation = schedulable.remove(random.nextInt(schedulable.size()));
      //Continuation continuation = schedulable.remove(0);
      continuation.run();
    }
  }
  
  static class Condition {
    private final Scheduler scheduler;
    private final ArrayDeque<Continuation> waitQueue = new ArrayDeque<>();
    
    Condition(Scheduler scheduler) {
      this.scheduler = scheduler;
    }
    
    public void await() {
      var currentContinuation = scheduler.currentContinuation();
      waitQueue.offer(currentContinuation);
      Continuation.yield(scheduler.scope);
    }
    
    public void signal() {
      scheduler.currentContinuation();  // check that this a thread has a continuation
      var continuation = waitQueue.poll();
      if (continuation == null) {
        return;
      }
      scheduler.schedulable.add(continuation);
    }
    
    public void signalAll() {
      scheduler.currentContinuation();  // check that this a thread has a continuation
      scheduler.schedulable.addAll(waitQueue);
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
    var scheduler = new Scheduler();
    var workQueue = new WorkQueue<Integer>(4, scheduler);
    scheduler.execute(() -> {
      IntStream.range(0, 10).forEach(id -> {
        scheduler.execute(() -> {
          for(;;) {
            System.out.println(id + ": produce " + id);
            workQueue.put(id);
            scheduler.pause();
          }
        });
      });
      IntStream.range(0, 10).forEach(id -> {
        scheduler.execute(() -> {
          for(;;) {
            System.out.println(id + ": consume " + workQueue.take());
            scheduler.pause();
          }
        });
      });
    });
  }
}
