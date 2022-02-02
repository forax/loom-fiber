package fr.umlv.loom.example;

import java.util.concurrent.StructuredTaskScope;

public class Example2 {
  // async calls with a value
  public static void main(String[] args) throws InterruptedException {
    try(var scope = StructuredTaskScope.open()) {
      var future1 = scope.fork(() -> {
        System.out.println(Thread.currentThread());
        return 40;
      });
      var future2 = scope.fork(() -> {
        System.out.println(Thread.currentThread());
        return 42;
      });
      scope.join();
      var sum = future1.resultNow() + future2.resultNow();
      System.out.println("sum = " + sum);
    }
  }
}
