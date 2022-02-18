package fr.umlv.loom.example;

import jdk.incubator.concurrent.StructuredTaskScope;

import java.util.concurrent.ExecutionException;

// $JAVA_HOME/bin/java --enable-preview --add-modules jdk.incubator.concurrent -cp target/loom-1.0-SNAPSHOT.jar  fr.umlv.loom.example._11_shutdown_on_success
public interface _11_shutdown_on_success {
  static void main(String[] args) throws InterruptedException, ExecutionException {
    try (var scope = new StructuredTaskScope.ShutdownOnSuccess<Integer>()) {
      var start = System.currentTimeMillis();
      var future1 = scope.fork(() -> {
        Thread.sleep(1_000);
        return 1;
      });
      var future2 = scope.fork(() -> {
        Thread.sleep(42);
        return 2;
      });
      scope.join();
      var end = System.currentTimeMillis();
      System.out.println("elapsed " + (end - start));
      //System.out.println(future1.resultNow());
      //System.out.println(future2.resultNow());
      System.out.println(scope.result());
    }
  }
}
