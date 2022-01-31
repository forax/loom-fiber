package fr.umlv.loom.example;

import java.util.concurrent.Future.State;
import java.util.concurrent.StructuredTaskScope;

public class Example5 {
  // async calls + testing the future state
  public static void main(String[] args) throws InterruptedException {
    var start = System.currentTimeMillis();
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
      var sum = (future1.state() == State.SUCCESS? future1.resultNow(): 0) +
          (future2.state() == State.SUCCESS? future2.resultNow(): 0);
      System.out.println("sum = " + sum);
    }
    var end = System.currentTimeMillis();
    System.out.println("elapsed time = " + (end - start) + " ms");
  }
}
