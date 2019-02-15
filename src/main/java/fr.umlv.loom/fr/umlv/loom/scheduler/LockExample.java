package fr.umlv.loom.scheduler;

import java.util.ArrayList;
import java.util.Objects;
import java.util.stream.IntStream;

public class LockExample {
  public static class Lock {
    private final Scheduler scheduler;
    private final ArrayList<Continuation> waitQueue = new ArrayList<>();
    private int depth;
    private Continuation owner;

    public Lock(Scheduler scheduler) {
      this.scheduler = Objects.requireNonNull(scheduler);
    }

    public void lock() {
      Continuation continuation = Scheduler.currentContinuation();
      for (;;) {
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
        Scheduler.yield();
      }
    }

    public void unlock() {
      Continuation continuation = Scheduler.currentContinuation();
      if (owner == continuation) {
        if (depth == 1) {
          depth = 0;
          owner = null;
          waitQueue.forEach(scheduler::register);
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
          for (;;) {
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
        for (;;) {
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
