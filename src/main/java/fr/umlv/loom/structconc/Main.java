package fr.umlv.loom.structconc;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredExecutor;

public class Main {
  public static void main(String[] args) throws ExecutionException, InterruptedException, IOException {
    // 1
    int value;
    try(var executor = StructuredExecutor.open()) {
      var shutdownOnSuccess = new ShutdownOnSuccess<Integer, IOException>(executor);
      //shutdownOnSuccess.fork(() -> 3);
      shutdownOnSuccess.fork(() -> { throw new IOException(); });
      value = shutdownOnSuccess.race();
    }
    System.out.println(value);

    // 2
    try(var executor = StructuredExecutor.open()) {
      var shutdownOnFailure = new ShutdownOnFailure(executor);
      var future1 = shutdownOnFailure.<Integer>fork(() -> { throw new IllegalStateException(); });
      var future2 = shutdownOnFailure.fork(() -> 4);
      shutdownOnFailure.join();
      value = future1.get() + future2.get();
    }
    System.out.println(value);
  }
}
