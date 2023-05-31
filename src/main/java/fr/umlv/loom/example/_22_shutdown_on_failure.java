package fr.umlv.loom.example;

import fr.umlv.loom.structured.StructuredScopeShutdownOnFailure;

// $JAVA_HOME/bin/java --enable-preview -cp target/classes  fr.umlv.loom.example._22_shutdown_on_failure
// docker run -it --rm --user forax -v /Users/forax:/home/forax -w /home/forax/git/loom-fiber fedora $JAVA_HOME/bin/java --enable-preview -cp target/classes fr.umlv.loom.example._22_shutdown_on_failure
public interface _22_shutdown_on_failure {
  static void main(String[] args) throws InterruptedException {
    try (var scope = new StructuredScopeShutdownOnFailure<RuntimeException>()) {
      var task1 = scope.fork(() -> {
        Thread.sleep(1_000);
        return 1;
      });
      var task2 = scope.fork(() -> {
        Thread.sleep(42);
        return 2;
      });
      scope.joinAll();
      System.out.println(task1.get() + task2.get());
    }
  }
}
