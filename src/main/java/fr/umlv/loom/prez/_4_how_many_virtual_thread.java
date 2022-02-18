package fr.umlv.loom.prez;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

// $JAVA_HOME/bin/java --enable-preview -cp target/loom-1.0-SNAPSHOT.jar  fr.umlv.loom.prez._4_how_many_virtual_thread
public interface _4_how_many_virtual_thread {
  private static void incrementALot() throws InterruptedException {
    var counter = new AtomicInteger();
    var threads = IntStream.range(0, 1_000_000)
        .mapToObj(i -> Thread.ofVirtual().unstarted(() -> {
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

  static void main(String[] args) throws InterruptedException {
    incrementALot();
  }
}
