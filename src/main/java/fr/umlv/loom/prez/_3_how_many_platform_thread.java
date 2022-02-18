package fr.umlv.loom.prez;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

// $JAVA_HOME/bin/java -cp target/loom-1.0-SNAPSHOT.jar  fr.umlv.loom.prez._3_how_many_platform_thread
public interface _3_how_many_platform_thread {
  private static void printHowManyThreads() throws BrokenBarrierException, InterruptedException {
    var barrier = new CyclicBarrier(1_000_001);
    for(var i = 0; i < 1_000_000; i++) {
      System.out.println(i);
      new Thread(() -> {
        try {
          barrier.await();
        } catch(InterruptedException | BrokenBarrierException e) {
          throw new AssertionError(e);
        }
      }).start();
    }
    barrier.await();
  }

  private static void incrementALot() throws InterruptedException {
    var counter = new AtomicInteger();
    var threads = IntStream.range(0, 1_000_000)
        .mapToObj(i -> Thread.ofPlatform().unstarted(() -> {
          try {
            Thread.sleep(1_000);
          } catch (InterruptedException e) {
            throw new AssertionError(e);
          }
          counter.incrementAndGet();
        }))
        .toList();
    for (var thread : threads) {
      thread.start();
    }
    for (var thread : threads) {
      thread.join();
    }
    System.out.println(counter);
  }

  static void main(String[] args) throws BrokenBarrierException, InterruptedException {
    printHowManyThreads();
    //incrementALot();
  }
}
