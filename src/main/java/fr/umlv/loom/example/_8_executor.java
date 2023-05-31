package fr.umlv.loom.example;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

// $JAVA_HOME/bin/java --enable-preview -cp target/loom-1.0-SNAPSHOT.jar  fr.umlv.loom.example._8_executor
public interface _8_executor {
  private static void simple() throws ExecutionException, InterruptedException {
    try(var executor = Executors.newVirtualThreadPerTaskExecutor()) {

      var future1 = executor.<Integer>submit(() -> {
        Thread.sleep(10);
        //return 1;
        throw new IOException("oops");
      });
      var future2 = executor.submit(() -> {
        Thread.sleep(1_000);
        return 2;
      });
      executor.shutdown();
      var result = future1.get() + future2.get();
      System.out.println(result);
      // everything is fine here, right ???
    }
  }

  static void main(String[] args) throws ExecutionException, InterruptedException {
    simple();
  }
}
