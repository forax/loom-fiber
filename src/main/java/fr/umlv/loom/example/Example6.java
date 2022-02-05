package fr.umlv.loom.example;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;

public class Example6 {
  // async calls + shutdownOnFailure
  public static void main(String[] args) throws InterruptedException, ExecutionException {
    var start = System.currentTimeMillis();
    try(var scope = new StructuredTaskScope.WithShutdownOnFailure()) {
      var future1 = scope.fork(() -> {
        Thread.sleep(1_000);
        return 101;
      });
      var future2 = scope.<Integer>fork(() -> {
        Thread.sleep(50);
        throw new RuntimeException("boom");
      });
      scope.join();
      scope.exception().ifPresentOrElse(Throwable::printStackTrace, () -> {
        var sum = future1.resultNow() + future2.resultNow();
        System.out.println("sum = " + sum);
      });
    }
    var end = System.currentTimeMillis();
    System.out.println("elapsed time = " + (end - start) + " ms");
  }
}
