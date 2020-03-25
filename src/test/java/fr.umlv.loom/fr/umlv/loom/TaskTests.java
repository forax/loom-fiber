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
public class TaskTests {
  @Test
  public void taskAsyncAwait() {
    var task = async(() -> 2);
    assertEquals(2, (int)task.join());
    assertFalse(task.isCancelled());
    assertTrue(task.isDone());
  }
  
  @Test
  public void taskAsyncAwaitParallel() {
    assertTimeout(Duration.ofMillis(250), () -> {
      var task = async(() -> sleep(200));
      var task2 = async(() -> sleep(200));
      task.join();
      task2.join();
    });
  }

  @Test
  public void taskException() {
    class FooException extends RuntimeException {
      private static final long serialVersionUID = 1L;
    }
    var task = async(() -> { throw new FooException(); });
    assertThrows(FooException.class, () -> task.join());
    assertFalse(task.isCancelled());
    assertTrue(task.isDone());
    assertThrows(FooException.class, () -> task.join());
    assertThrows(FooException.class, () -> task.await(Duration.ofNanos(0)));
    assertThrows(ExecutionException.class, () -> task.get());
    assertThrows(ExecutionException.class, () -> task.get(0, TimeUnit.MILLISECONDS));
  }
  
  @Test
  public void taskCancelled() {
    var task = async(() -> sleep(100));
    assertTrue(task.cancel(false));
    assertTrue(task.isCancelled());
    assertTrue(task.isDone());
    assertThrows(CancellationException.class, () -> task.join());
    assertThrows(CancellationException.class, () -> task.join());
  }
  
  @Test
  public void taskTimeouted() {
    var task = async(() -> sleep(100));
    assertThrows(TimeoutException.class, () -> task.get(10, TimeUnit.MILLISECONDS));
    assertTrue(task.isCancelled());
    assertTrue(task.isDone());
    assertThrows(CancellationException.class, () -> task.get(10, TimeUnit.MILLISECONDS));
    assertThrows(CancellationException.class, () -> task.join());
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
