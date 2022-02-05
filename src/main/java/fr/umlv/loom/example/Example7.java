package fr.umlv.loom.example;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;

public class Example7 {
  // async calls + shutdownOnSuccess
  public static void main(String[] args) throws InterruptedException, ExecutionException {
    var start = System.currentTimeMillis();
    try(var scope = new StructuredTaskScope.WithShutdownOnSuccess<Integer>()) {
      var future1 = scope.fork(() -> {
        Thread.sleep(1_000);
        return 101;
      });
      var future2 = scope.fork(() -> {
        Thread.sleep(50);
        return 606;
      });
      scope.join();
      System.out.println("result = " + scope.result());
    }
    var end = System.currentTimeMillis();
    System.out.println("elapsed time = " + (end - start) + " ms");
  }
}
