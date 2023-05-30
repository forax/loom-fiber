package fr.umlv.loom.example;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

// $JAVA_HOME/bin/java -cp target/classes  fr.umlv.loom.example._4_how_many_virtual_thread
public interface _4_how_many_virtual_thread {
  static void main(String[] args) throws InterruptedException {
    var counter = new AtomicInteger();
    var barrier = new CyclicBarrier(100_000);
    var threads = IntStream.range(0, 100_000)
        .mapToObj(i -> Thread.ofVirtual().unstarted(() -> {
          try {
            //Thread.sleep(5_000);
            barrier.await();
          } catch (InterruptedException | BrokenBarrierException e) {
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
}
