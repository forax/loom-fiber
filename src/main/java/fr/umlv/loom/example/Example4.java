package fr.umlv.loom.example;

import java.util.concurrent.StructuredTaskScope;

public class Example4 {
  // async calls with exception
  public static void main(String[] args) throws InterruptedException {
    try(var executor = StructuredTaskScope.open()) {
      var future1 = executor.fork(() -> {
        Thread.sleep(1_000);
        return 101;
      });
      var future2 = executor.<Integer>fork(() -> {
        Thread.sleep(50);
        throw new RuntimeException("boom");
      });
      executor.join();
      var sum = future1.resultNow() + future2.resultNow();
      System.out.println("sum = " + sum);
    }
  }
}
