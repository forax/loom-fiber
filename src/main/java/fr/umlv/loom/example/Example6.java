package fr.umlv.loom.example;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.ShutdownOnFailure;

public class Example6 {
  // async calls + shutdownOnFailure
  public static void main(String[] args) throws InterruptedException, ExecutionException {
    var start = System.currentTimeMillis();
    try(var completionPolicy = new ShutdownOnFailure();
        var executor = StructuredTaskScope.open(completionPolicy)) {
      var future1 = executor.fork(() -> {
        Thread.sleep(1_000);
        return 101;
      });
      var future2 = executor.<Integer>fork(() -> {
        Thread.sleep(50);
        throw new RuntimeException("boom");
      });
      executor.join();
      completionPolicy.exception().ifPresentOrElse(Throwable::printStackTrace, () -> {
        var sum = future1.resultNow() + future2.resultNow();
        System.out.println("sum = " + sum);
      });
    }
    var end = System.currentTimeMillis();
    System.out.println("elapsed time = " + (end - start) + " ms");
  }
}
