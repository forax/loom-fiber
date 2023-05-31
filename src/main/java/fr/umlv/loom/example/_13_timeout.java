package fr.umlv.loom.example;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeoutException;

// $JAVA_HOME/bin/java --enable-preview -cp target/classes  fr.umlv.loom.example._13_timeout
public interface _13_timeout {
  static void main(String[] args) throws InterruptedException, ExecutionException {
    try (var scope = new StructuredTaskScope<>()) {
      var task1 = scope.fork(() -> {
        Thread.sleep(1_000); // throws InterruptedException
        return 1;
      });
      var task2 = scope.fork(() -> {
        Thread.sleep(5_000);  // throws InterruptedException
        return 2;
      });
      try {
        scope.joinUntil(Instant.now().plus(Duration.ofMillis(100)));
      } catch (TimeoutException e) {
        //scope.shutdown();
      }
      System.out.println(task1.state());  // UNAVAILABLE
      System.out.println(task2.state());  // UNAVAILABLE
    }
  }
}
