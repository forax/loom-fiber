package fr.umlv.loom.scheduler;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

public class RandomScheduler {
  final ContinuationScope scope;
  final ArrayList<Continuation> schedulable = new ArrayList<>();
  
  public RandomScheduler() {
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
    ThreadLocalRandom random = ThreadLocalRandom.current();
    while(!schedulable.isEmpty()) {
      int index = random.nextInt(schedulable.size());
      //System.out.println("schedule " + schedulable.get(index));
      Continuation continuation = schedulable.remove(index);
      continuation.run();
    }
  }
  
  static class Lock {
    private final RandomScheduler scheduler;
    private final ArrayList<Continuation> waitQueue = new ArrayList<>();
    private int depth;
    private Continuation owner;
    
    public Lock(RandomScheduler scheduler) {
      this.scheduler = Objects.requireNonNull(scheduler);
    }

    public void lock() {
      Continuation continuation = scheduler.currentContinuation();
      for(;;) {
        if (depth == 0) {
          depth = 1;
          owner = continuation;
          break;
        }
        if (owner == continuation) {
          depth++;
          break;
        }
        waitQueue.add(continuation);
        Continuation.yield(scheduler.scope);
      }
    }
    
    public void unlock() {
      Continuation continuation = scheduler.currentContinuation();
      if (owner == continuation) {
        if (depth == 1) {
          depth = 0;
          owner = null;
          scheduler.schedulable.addAll(waitQueue);
          waitQueue.clear();
        } else {
          depth--;
        }
      } else {
        throw new IllegalStateException("not locked !");
      }
    }
  }
  
  public static void main(String[] args) {
    var scheduler = new RandomScheduler();
    var lock = new Lock(scheduler);
    var shared = new Object() {
      int x;
      int y;
    };
    scheduler.execute(() -> {
      IntStream.range(0, 2).forEach(id -> {
        scheduler.execute(() -> {
          for(;;) {
            lock.lock();
            try {
              shared.x = id;
              scheduler.pause();
              shared.y = id;
            } finally {
              lock.unlock();
            }
            scheduler.pause();
          }
        });
      });
      scheduler.execute(() -> {
        for(;;) {
          lock.lock();
          try {
            System.out.println(shared.x + " " + shared.y);  
          } finally {
            lock.unlock();
          }
          scheduler.pause();
        }
      });
    });
  }
}
