package fr.umlv.loom.prez;

import jdk.incubator.concurrent.StructuredTaskScope;

public interface _8_structured_concurrency {
  static void main(String[] args) throws InterruptedException {
    try (var scope = new StructuredTaskScope<>()) {
      var start = System.currentTimeMillis();
      var future1 = scope.fork(() -> {
        Thread.sleep(1_000);
        return "task1";
      });
      var future2 = scope.fork(() -> {
        Thread.sleep(1_000);
        return "task2";
      });
      scope.join();
      var end = System.currentTimeMillis();
      System.out.println("elapsed " + (end - start));
      System.out.println(future1.resultNow());
      System.out.println(future2.resultNow());
    } // call close() !
  }
}
