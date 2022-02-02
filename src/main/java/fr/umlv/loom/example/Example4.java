package fr.umlv.loom.example;

import java.util.concurrent.StructuredTaskScope;

public class Example4 {
  // async calls with exception
  public static void main(String[] args) throws InterruptedException {
    try(var scope = StructuredTaskScope.open()) {
      var future1 = scope.fork(() -> {
        Thread.sleep(1_000);
        return 101;
      });
      var future2 = scope.<Integer>fork(() -> {
        Thread.sleep(50);
        throw new RuntimeException("boom");
      });
      scope.join();
      var sum = future1.resultNow() + future2.resultNow();
      System.out.println("sum = " + sum);
    }
  }
}
