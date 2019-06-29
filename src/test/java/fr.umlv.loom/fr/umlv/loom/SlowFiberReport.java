package fr.umlv.loom;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class SlowFiberReport {
  private static final boolean LOGGING_ENABLED = false;
  private static final int THREAD_COUNT = 1;
  private static final int WORKER_COUNT = 50;
  private static final int MESSAGE_PASSING_COUNT = 1_000_000;

  private static class Worker implements Runnable {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notWaiting = lock.newCondition();

    private final int id;
    private final int[] sequences;
    private Worker next;

    private boolean waiting = true;
    private int sequence;

    private Worker(int id, int[] sequences) {
      this.id = id;
      this.sequences = sequences;
    }

    private void setNext(Worker next) {
      this.next = next;
    }

    private void send(int sequence) {
      log("[%2d] locking next", id);
      lock.lock();
      try {
        log("[%2d] signaling next (sequence=%d)", id, sequence);
        if (!waiting) {
          throw new IllegalStateException(id + " should be in waiting state");
        }
        this.sequence = sequence;
        waiting = false;
        notWaiting.signal();
      } finally {
        log("[%2d] unlocking", id);
        lock.unlock();
      }
    }

    private boolean await() throws InterruptedException {
      log("[%2d] locking", id);
      lock.lock();
      try {
        while (waiting) {
          log("[%2d] awaiting", id);
          notWaiting.await();
          log("[%2d] woke up", id);
        }
        next.send(sequence - 1);
        waiting = true;
        if (sequence <= 0) {
          sequences[id] = sequence;
          return true;
        }
        return false;
      } finally {
        lock.unlock();
      }
    }

    @Override
    public void run() {
      try {
        for (;;) {
          if (await()) {
            return;
          }
        }
      } catch (InterruptedException e) {
        throw new AssertionError(e);
      }
    }
  }

  public static void main(String[] args) throws InterruptedException {
    var startTimeMillis = System.currentTimeMillis();
    runThreads();
    System.out.format("threads took %d ms%n", System.currentTimeMillis() - startTimeMillis);

    startTimeMillis = System.currentTimeMillis();
    runFibers();
    System.out.format("fibers took %d ms%n", System.currentTimeMillis() - startTimeMillis);
  }

  private static int[] runThreads() throws InterruptedException {
    log("creating worker threads (WORKER_COUNT=%d)", WORKER_COUNT);
    var sequences = new int[WORKER_COUNT];
    var workers = new Worker[WORKER_COUNT];
    
    for (var workerIndex = 0; workerIndex < WORKER_COUNT; workerIndex++) {
      var worker = new Worker(workerIndex, sequences);
      workers[workerIndex] = worker;
    }

    log("setting next worker pointers");
    for (var workerIndex = 0; workerIndex < WORKER_COUNT; workerIndex++) {
      workers[workerIndex].setNext(workers[(workerIndex + 1) % WORKER_COUNT]);
    }

    log("starting threads");
    var threads = new Thread[WORKER_COUNT];
    for (var workerIndex = 0; workerIndex < WORKER_COUNT; workerIndex++) {
      var thread = new Thread(workers[workerIndex]);
      threads[workerIndex] = thread;
      thread.start();
    }

    log("ensuring threads are started and waiting");
    for (var thread : threads) {
      while (thread.getState() != Thread.State.WAITING) {
        // empty
      }
    }

    log("initiating the ring (MESSAGE_PASSING_COUNT=%d)", MESSAGE_PASSING_COUNT);
    workers[0].send(MESSAGE_PASSING_COUNT);

    log("waiting for threads to complete");
    for (var thread : threads) {
      thread.join();
    }

    log("returning populated sequences");
    return sequences;
  }

  private static int[] runFibers() throws InterruptedException {
    log("creating executor service (THREAD_COUNT=%d)", THREAD_COUNT);
    var executorService = Executors.newFixedThreadPool(THREAD_COUNT);

    log("creating workers (WORKER_COUNT=%d)", WORKER_COUNT);
    var workers = new Worker[WORKER_COUNT];
    var sequences = new int[WORKER_COUNT];
    for (var workerIndex = 0; workerIndex < WORKER_COUNT; workerIndex++) {
      workers[workerIndex] = new Worker(workerIndex, sequences); 
    }

    log("setting \"next\" worker pointers");
    for (int workerIndex = 0; workerIndex < WORKER_COUNT; workerIndex++) {
      workers[workerIndex].setNext(workers[(workerIndex + 1) % WORKER_COUNT]);
    }
    
    log("starting fibers");
    var fibers = new Fiber<?>[WORKER_COUNT];
    for (var workerIndex = 0; workerIndex < WORKER_COUNT; workerIndex++) {
      fibers[workerIndex] = FiberScope.background().schedule(executorService, workers[workerIndex]);
    }

    log("initiating the ring (MESSAGE_PASSING_COUNT=%d)", MESSAGE_PASSING_COUNT);
    workers[0].send(MESSAGE_PASSING_COUNT);

    log("waiting for workers to complete");
    for (var workerIndex = 0; workerIndex < WORKER_COUNT; workerIndex++) {
      var fiber = fibers[workerIndex];
      fiber.join();
    }

    log("shutting down the executor service");
    executorService.shutdown();

    log("returning populated sequences");
    return sequences;
  }
   

  private static synchronized void log(String fmt, Object... args) {
    if (LOGGING_ENABLED) {
      System.out.format(Instant.now() + " [" + Thread.currentThread().getName() + "] " + fmt + "%n", args);
    }
  }
}