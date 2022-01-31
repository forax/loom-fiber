package fr.umlv.loom.executor;

/*
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class VirtualThreadExecutorTest {

  @Test
  public void shutdown() throws ExecutionException, InterruptedException {
    var executor = new VirtualThreadExecutor();
    var future = executor.submit(() -> {
      Thread.sleep(100);
      return "hello";
    });
    executor.shutdown();
    assertEquals("hello", future.get());
  }

  @Test
  public void shutdownNow() throws InterruptedException {
    var executor = new VirtualThreadExecutor();
    var future = executor.submit(() -> {
      Thread.sleep(1_000);
      return "hello";
    });
    executor.shutdownNow();
    try {
      future.get();
      fail();
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof InterruptedException);
    }
  }

  @Test
  public void isShutdown() {
    var executor = new VirtualThreadExecutor();
    executor.shutdown();
    assertTrue(executor.isShutdown());
  }

  @Test
  public void isTerminated() {
    var executor = new VirtualThreadExecutor();
    executor.awaitTermination(1, TimeUnit.SECONDS);
    assertTrue(executor.isTerminated());
  }

  @Test
  public void awaitTermination() {
    var executor = new VirtualThreadExecutor();
    var future = executor.submit(() -> {
      Thread.sleep(1);
      return "hello";
    });
    executor.awaitTermination(1, TimeUnit.SECONDS);
    assertEquals("hello", future.resultNow());
  }

  @Test
  public void execute() {
    var executor = new VirtualThreadExecutor();
    var result = new AtomicBoolean();
    executor.execute(() -> result.set(true));
    executor.awaitTermination(1, TimeUnit.SECONDS);
    assertTrue(result.get());
  }

  @Test
  public void submitCallableResult() throws ExecutionException, InterruptedException {
    var executor = new VirtualThreadExecutor();
    var future = executor.submit(() -> {
      Thread.sleep(50);
      return 42;
    });
    executor.shutdown();
    assertEquals(42, (int) future.get());
  }
  @Test
  public void submitCallableException() throws InterruptedException {
    var executor = new VirtualThreadExecutor();
    var future = executor.submit(() -> {
      Thread.sleep(50);
      throw new RuntimeException("oops");
    });
    executor.shutdown();
    try {
      future.get();
      fail();
    } catch(ExecutionException e) {
      assertTrue(e.getCause() instanceof RuntimeException);
    }
  }

  @Test
  public void testSubmitRunnable() throws ExecutionException, InterruptedException {
    var executor = new VirtualThreadExecutor();
    var result = new AtomicBoolean();
    var future = executor.submit((Runnable) () -> result.set(true));
    executor.shutdown();
    assertNull(future.get());
    assertTrue(result.get());
  }

  @Test
  public void testSubmitRunnableWithValue() throws ExecutionException, InterruptedException {
    var executor = new VirtualThreadExecutor();
    var result = new AtomicBoolean();
    var future = executor.submit((Runnable) () -> result.set(true), 42);
    executor.shutdown();
    assertEquals(42, (int) future.get());
    assertTrue(result.get());
  }

  @Test
  public void invokeAll() throws InterruptedException {
    var executor = new VirtualThreadExecutor();
    var tasks = List.<Callable<Integer>>of(
        () -> {
          Thread.sleep(1_000);
          return 1;
        },
        () -> {
          Thread.sleep(1_000);
          return 101;
        });
    var list = executor.invokeAll(tasks);
    executor.shutdown();
    assertEquals(List.of(1, 101), list.stream().map(Future::resultNow).toList());
  }

  @Test
  public void invokeAllWithTimeout() throws InterruptedException {
    var executor = new VirtualThreadExecutor();
    var tasks = List.<Callable<Integer>>of(
        () -> 1,
        () -> 101
    );
    var list = executor.invokeAll(tasks, 1, TimeUnit.SECONDS);
    executor.shutdown();
    assertEquals(List.of(1, 101), list.stream().map(Future::resultNow).toList());
  }
  @Test
  public void invokeAllWithTimeoutDeadlineReached() throws InterruptedException {
    var executor = new VirtualThreadExecutor();
    var tasks = List.<Callable<Integer>>of(
        () -> {
          Thread.sleep(1_000);
          return 1;
        },
        () -> {
          return 101;
        });
    var list = executor.invokeAll(tasks, 500, TimeUnit.MILLISECONDS);
    executor.shutdown();
    assertEquals(List.of(101), list.stream().map(Future::resultNow).toList());
  }

  @Test
  public void invokeAny() throws ExecutionException, InterruptedException, TimeoutException {
    var executor = new VirtualThreadExecutor();
    var tasks = List.<Callable<Integer>>of(
        () -> {
          return 42;
        },
        () -> {
          Thread.sleep(1_000);
          return 101;
        });
    var result = executor.invokeAny(tasks);
    executor.shutdown();
    assertEquals(42, (int) result);
  }

  @Test
  public void invokeAnyWithTimeout() throws ExecutionException, InterruptedException, TimeoutException {
    var executor = new VirtualThreadExecutor();
    var tasks = List.<Callable<Integer>>of(
        () -> {
          Thread.sleep(50);
          return 42;
        },
        () -> {
          return 101;
        });
    var result = executor.invokeAny(tasks, 1_000, TimeUnit.MILLISECONDS);
    executor.shutdown();
    assertEquals(101, (int) result);
  }
  @Test
  public void invokeAnyWithTimeoutDeadlineReached() throws ExecutionException, InterruptedException, TimeoutException {
    var executor = new VirtualThreadExecutor();
    var tasks = List.<Callable<Integer>>of(
        () -> {
          Thread.sleep(1_000);
          return 42;
        },
        () -> {
          Thread.sleep(1_000);
          return 101;
        });
    assertThrows(TimeoutException.class, () -> executor.invokeAny(tasks, 50, TimeUnit.MILLISECONDS));
    executor.shutdown();
  }
}
 */