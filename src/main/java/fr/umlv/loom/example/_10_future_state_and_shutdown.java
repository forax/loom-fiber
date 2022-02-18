package fr.umlv.loom.example;

import jdk.incubator.concurrent.StructuredTaskScope;

// $JAVA_HOME/bin/java --enable-preview --add-modules jdk.incubator.concurrent -cp target/loom-1.0-SNAPSHOT.jar  fr.umlv.loom.example._10_future_state_and_shutdown
public interface _10_future_state_and_shutdown {
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
