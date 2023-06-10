package fr.umlv.loom.example;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;

// $JAVA_HOME/bin/java --enable-preview -cp target/classes fr.umlv.loom.example._11_shutdown_on_success
public interface _11_shutdown_on_success {
  static void main(String[] args) throws InterruptedException, ExecutionException {
    try (var scope = new StructuredTaskScope.ShutdownOnSuccess<Integer>()) {
      scope.fork(() -> {
        Thread.sleep(1_000);
        return 1;
      });
      scope.fork(() -> {
        Thread.sleep(42);
        return 2;
      });
      var result = scope.join().result();
      System.out.println(result);
    }
  }
}
