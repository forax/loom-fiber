package fr.umlv.loom.example;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;

// $JAVA_HOME/bin/java --enable-preview -cp target/classes  fr.umlv.loom.example._12_shutdown_on_failure
// docker run -it --rm --user forax -v /Users/forax:/home/forax -w /home/forax/git/loom-fiber fedora $JAVA_HOME/bin/java --enable-preview -cp target/classes fr.umlv.loom.example._12_shutdown_on_failure
public interface _12_shutdown_on_failure {
  static void main(String[] args) throws InterruptedException, ExecutionException {
    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
      var task1 = scope.fork(() -> {
        Thread.sleep(1_000);
        return 1;
      });
      var task2 = scope.fork(() -> {
        Thread.sleep(42);
        return 2;
      });
      scope.join().throwIfFailed();
      System.out.println(task1.get() + task2.get());
    }
  }
}
