package fr.umlv.loom;

import static fr.umlv.loom.Task.async;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

@SuppressWarnings("static-method")
class TaskTests {
  @Test
  void taskAsyncAwait() {
    var task = async(() -> 2);
    assertEquals(2, (int)task.await());
    assertFalse(task.isCancelled());
    assertTrue(task.isDone());
  }
  
  @Test
  void taskAsyncAwaitParallel() {
    assertTimeout(Duration.ofMillis(1_500), () -> {
      var task = async(() -> sleep(1_000));
      var task2 = async(() -> sleep(1_000));
      task.await();
      task2.await();
    });
  }

  @Test
  void taskException() {
    class FooException extends RuntimeException {
      private static final long serialVersionUID = 1L;
    }
    var task = async(() -> { throw new FooException(); });
    assertThrows(FooException.class, () -> task.await());
    assertFalse(task.isCancelled());
    assertTrue(task.isDone());
    assertThrows(FooException.class, () -> task.await());
    assertThrows(FooException.class, () -> task.await(Duration.ofNanos(0)));
    assertThrows(ExecutionException.class, () -> task.get());
    assertThrows(ExecutionException.class, () -> task.get(0, TimeUnit.MILLISECONDS));
  }
  
  @Test
  void taskCancelled() {
    var task = async(() -> sleep(1_000));
    assertTrue(task.cancel(false));
    assertTrue(task.isCancelled());
    assertTrue(task.isDone());
    assertThrows(CancellationException.class, () -> task.await());
    assertThrows(CancellationException.class, () -> task.await());
  }
  
  @Test
  void taskTimeouted() {
    var task = async(() -> sleep(1_000));
    assertThrows(TimeoutException.class, () -> task.get(10, TimeUnit.MILLISECONDS));
    assertTrue(task.isCancelled());
    assertTrue(task.isDone());
    assertThrows(CancellationException.class, () -> task.get(10, TimeUnit.MILLISECONDS));
    assertThrows(CancellationException.class, () -> task.await());
    assertThrows(CancellationException.class, () -> task.get());
  }
  
  private static long sleep(int millis) {
    var start = System.nanoTime();
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }
    var end = System.nanoTime();
    return end - start;
  }
}
