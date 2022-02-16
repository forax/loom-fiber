package fr.umlv.loom.prez;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public interface _7_executor {
  static void main(String[] args) throws ExecutionException, InterruptedException {
    var executor = Executors.newCachedThreadPool();
    //var executor = Executors.newVirtualThreadPerTaskExecutor();

    var start = System.currentTimeMillis();
    var future1 = executor.submit(() -> {
      Thread.sleep(1_000);
      return "task1";
    });
    var future2 = executor.submit(() -> {
      Thread.sleep(1_000);
      return "task2";
    });
    executor.shutdown();
    var result1 = future1.get();
    var result2 = future2.get();
    var end = System.currentTimeMillis();
    System.out.println("elapsed " + (end - start));
    System.out.println(result1);
    System.out.println(result2);
  }
}
