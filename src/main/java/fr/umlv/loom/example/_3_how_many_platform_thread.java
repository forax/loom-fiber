package fr.umlv.loom.example;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

// $JAVA_HOME/bin/java -cp target/loom-1.0-SNAPSHOT.jar  fr.umlv.loom.example._3_how_many_platform_thread
public interface _3_how_many_platform_thread {
  static void main(String[] args) throws BrokenBarrierException, InterruptedException {
    var threads = IntStream.range(0, 100_000)
        .mapToObj(i -> new Thread(() -> {
          try {
            Thread.sleep(5_000);
          } catch (InterruptedException e) {
            throw new AssertionError(e);
          }
        }))
        .toList();
    var i = 0;
    for (var thread: threads) {
      System.out.println(i++);
      thread.start();
    }
    for (var thread : threads) {
      thread.join();
    }
  }
}
