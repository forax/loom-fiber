package fr.umlv.loom.prez;

import jdk.incubator.concurrent.StructuredTaskScope;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public interface _11_timeout {
  static void main(String[] args) throws InterruptedException, ExecutionException {
    try (var scope = new StructuredTaskScope<>()) {
      var start = System.currentTimeMillis();
      var future1 = scope.fork(() -> {
        Thread.sleep(1_000); // throws InterruptedException
        return 1;
      });
      var future2 = scope.fork(() -> {
        Thread.sleep(5_000);  // throws InterruptedException
        return 2;
      });
      try {
        scope.joinUntil(Instant.now().plus(Duration.ofMillis(100)));
      } catch (TimeoutException e) {
        scope.shutdown();
      }
      var end = System.currentTimeMillis();
      System.out.println("elapsed " + (end - start));
      System.out.println(future1.state());  // FAILED
      System.out.println(future2.state());  // FAILED
    }
  }
}
