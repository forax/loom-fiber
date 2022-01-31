package fr.umlv.loom.example;

import java.util.concurrent.StructuredTaskScope;

public class Example2 {
  // async calls with a value
  public static void main(String[] args) throws InterruptedException {
    try(var executor = StructuredTaskScope.open()) {
      var future1 = executor.fork(() -> {
        System.out.println(Thread.currentThread());
        return 40;
      });
      var future2 = executor.fork(() -> {
        System.out.println(Thread.currentThread());
        return 42;
      });
      executor.join();
      var sum = future1.resultNow() + future2.resultNow();
      System.out.println("sum = " + sum);
    }
  }
}
