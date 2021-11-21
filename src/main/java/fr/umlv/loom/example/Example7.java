package fr.umlv.loom.example;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredExecutor;
import java.util.concurrent.StructuredExecutor.ShutdownOnFailure;
import java.util.concurrent.StructuredExecutor.ShutdownOnSuccess;

public class Example7 {
  // async calls + shutdownOnSuccess
  public static void main(String[] args) throws InterruptedException, ExecutionException {
    var start = System.currentTimeMillis();
    try(var executor = StructuredExecutor.open()) {
      var shutdownOnSuccess = new ShutdownOnSuccess<Integer>();
      var future1 = executor.fork(() -> {
        Thread.sleep(1_000);
        return 101;
      }, shutdownOnSuccess);
      var future2 = executor.fork(() -> {
        Thread.sleep(50);
        return 606;
      }, shutdownOnSuccess);
      executor.join();
      System.out.println("result = " + shutdownOnSuccess.result());
    }
    var end = System.currentTimeMillis();
    System.out.println("elapsed time = " + (end - start) + " ms");
  }
}
