package fr.umlv.loom.example;

import fr.umlv.loom.structured.StructuredScopeShutdownOnSuccess;

import java.io.IOException;

// $JAVA_HOME/bin/java --enable-preview -cp target/classes fr.umlv.loom.example._21_shutdown_on_success
public interface _21_shutdown_on_success {
  static void main(String[] args) throws IOException, InterruptedException {
    try (var scope = new StructuredScopeShutdownOnSuccess<Integer, IOException>()) {
      scope.fork(() -> {
        Thread.sleep(1_000);
        return 1;
      });
      scope.fork(() -> {
        Thread.sleep(42);
        return 2;
      });
      var result = scope.joinAll();
      System.out.println(result);
    }
  }
}
