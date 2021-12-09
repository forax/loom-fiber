package fr.umlv.loom.example;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Future.State;
import java.util.concurrent.StructuredExecutor;
import java.util.concurrent.StructuredExecutor.ShutdownOnFailure;

public class Example6 {
  // async calls + shutdownOnFailure
  public static void main(String[] args) throws InterruptedException, ExecutionException {
    var start = System.currentTimeMillis();
    try(var executor = StructuredExecutor.open()) {
      var shutdownOnFailure = new ShutdownOnFailure();
      var future1 = executor.fork(() -> {
        Thread.sleep(1_000);
        return 101;
      }, shutdownOnFailure);
      var future2 = executor.<Integer>fork(() -> {
        Thread.sleep(50);
        throw new RuntimeException("boom");
      }, shutdownOnFailure);
      executor.join();
      shutdownOnFailure.exception().ifPresentOrElse(Throwable::printStackTrace, () -> {
        var sum = future1.resultNow() + future2.resultNow();
        System.out.println("sum = " + sum);
      });
    }
    var end = System.currentTimeMillis();
    System.out.println("elapsed time = " + (end - start) + " ms");
  }
}
