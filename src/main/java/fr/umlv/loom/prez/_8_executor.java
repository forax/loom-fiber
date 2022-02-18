package fr.umlv.loom.prez;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

// $JAVA_HOME/bin/java --enable-preview -cp target/loom-1.0-SNAPSHOT.jar  fr.umlv.loom.prez._8_executor
public interface _8_executor {
  private static void simple() throws ExecutionException, InterruptedException {
    var executor = Executors.newCachedThreadPool();
    //var executor = Executors.newVirtualThreadPerTaskExecutor();

    var future1 = executor.submit(() -> {
      Thread.sleep(10);
      return 1;
    });
    var future2 = executor.submit(() -> {
      Thread.sleep(1_000);
      return 2;
    });
    executor.shutdown();
    var result = future1.get() + future2.get();
    System.out.println(result);
    // everything is fine here, right !
  }

  private static void runningTask1() throws ExecutionException, InterruptedException {
    var executor = Executors.newCachedThreadPool();
    var future1 = executor.submit(() -> {
      Thread.sleep(10);
      return 1;
    });
    var future2 = executor.submit(() -> {
      Thread.sleep(1_000);
      System.out.println("end");
      return 2;
    });
    executor.shutdown();
    //var result = future1.get() + future2.get();
    var result = future1.get();
    System.out.println(result);
    // future2 still running here !
  }

  private static void runningTask2() throws InterruptedException {
    var executor = Executors.newCachedThreadPool();
    var future1 = executor.<Integer>submit(() -> {
      throw new AssertionError("oops");
    });
    var future2 = executor.submit(() -> {
      Thread.sleep(1_000);
      System.out.println("end");
      return 2;
    });
    executor.shutdown();
    try {
      var result = future1.get() + future2.get();
      System.out.println(result);
    } catch(ExecutionException e) {
      throw new AssertionError(e.getCause());
    }
    // future2 still running here !
  }

  static void main(String[] args) throws ExecutionException, InterruptedException {
    simple();
    //runningTask1();
    //runningTask2();
  }
}
