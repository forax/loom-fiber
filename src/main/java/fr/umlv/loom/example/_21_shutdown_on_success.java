package fr.umlv.loom.example;

import fr.umlv.loom.structured.StructuredScopeShutdownOnSuccess;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;

// $JAVA_HOME/bin/java --enable-preview -cp target/classes fr.umlv.loom.example._21_shutdown_on_success
// docker run -it --rm --user forax -v /Users/forax:/home/forax -w /home/forax/git/loom-fiber fedora $JAVA_HOME/bin/java --enable-preview -cp target/classes fr.umlv.loom.example._21_shutdown_on_success
public interface _21_shutdown_on_success {
  static void main(String[] args) throws InterruptedException, ExecutionException {
    try (var scope = new StructuredScopeShutdownOnSuccess<Integer, RuntimeException>()) {
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
