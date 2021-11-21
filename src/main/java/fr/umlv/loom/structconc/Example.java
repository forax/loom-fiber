package fr.umlv.loom.structconc;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredExecutor;

public class Example {
  public static void main(String[] args) throws InterruptedException, ExecutionException {
    try(var executor = StructuredExecutor.open()) {
      try(var executor2 = StructuredExecutor.open()) {
        var handler = new StructuredExecutor.ShutdownOnSuccess<Integer>();
        executor2.fork(() -> 3, handler);
        executor2.fork(() -> 7, handler);
        executor2.join();
        System.out.println(handler.result());
      }
      executor.join();
    }
  }
}
