package fr.umlv.loom.example;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;

// $JAVA_HOME/bin/java --enable-preview -cp target/classes  fr.umlv.loom.example._11_shutdown_on_success
// docker run -it --rm --user forax -v /Users/forax:/home/forax -w /home/forax/git/loom-fiber fedora $JAVA_HOME/bin/java --enable-preview -cp target/classes fr.umlv.loom.example._11_shutdown_on_success
public interface _12_shutdown_on_failure {
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
      var result = scope.join().result();
      var end = System.currentTimeMillis();
      System.out.println("elapsed " + (end - start));
      System.out.println(result);
    }
  }
}
