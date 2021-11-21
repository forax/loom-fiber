package fr.umlv.loom.executor;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class UnsafeExecutorsTest {
  private static String carrierThreadName() {
    var name = Thread.currentThread().toString();
    var index = name.lastIndexOf('@');
    if (index == -1) {
      throw new AssertionError();
    }
    return name.substring(index + 1);
  }

  @Test
  public void virtualThreadExecutorSingleThreadExecutor() throws InterruptedException {
    var executor = Executors.newSingleThreadExecutor();
    var virtualExecutor = UnsafeExecutors.virtualThreadExecutor(executor);
    var carrierThreadNames = new CopyOnWriteArraySet<String>();
    for(var i = 0; i < 10; i++) {
      virtualExecutor.execute(() -> carrierThreadNames.add(carrierThreadName()));
    }
    executor.shutdown();
    executor.awaitTermination(1, TimeUnit.DAYS);
    assertEquals(1, carrierThreadNames.size());
  }
}