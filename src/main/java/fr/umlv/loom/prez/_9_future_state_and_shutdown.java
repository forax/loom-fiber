package fr.umlv.loom.prez;

import jdk.incubator.concurrent.StructuredTaskScope;

public interface _9_future_state_and_shutdown {
  static void main(String[] args) throws InterruptedException {
    try (var scope = new StructuredTaskScope<>()) {
      var future = scope.fork(() -> {
        Thread.sleep(1_000);
        return 42;
      });
      System.out.println(future.state());  // RUNNING
      //scope.shutdown();
      scope.join();
      System.out.println(future.state());  // SUCCESS
    }
  }
}
