package fr.umlv.loom.structured;

import fr.umlv.loom.structured.AsyncScope.Result;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class AsyncScopeTest {

  @Test
  public void oneTaskSuccess() throws InterruptedException{
    try(var scope = new AsyncScope<Integer, RuntimeException>()) {
      var task = scope.async(() -> {
        Thread.sleep(100);
        return 10;
      });

      scope.awaitAll();

      int value = task.getNow();
      assertEquals(10, value);
    }
  }

  @Test
  public void oneTaskResultSuccess() throws InterruptedException{
    try(var scope = new AsyncScope<Integer, RuntimeException>()) {
      var task = scope.async(() -> {
        Thread.sleep(100);
        return 10;
      });

      scope.awaitAll();

      var result = task.result();
      switch (result.state()) {
        case SUCCESS -> assertEquals(10, result.result());
        case FAILED -> fail();
      }
    }
  }

  @Test
  public void oneTaskResultSuccess2() throws InterruptedException{
    try(var scope = new AsyncScope<Integer, RuntimeException>()) {
      var task = scope.async(() -> {
        Thread.sleep(100);
        return 10;
      });

      scope.awaitAll();

      assertAll(
          () -> assertEquals(10, task.getNow()),
          () -> assertEquals(10, task.result().getNow()),
          () -> assertEquals(Result.State.SUCCESS, task.result().state()),
          () -> assertTrue(task.result().isSuccess()),
          () -> assertFalse(task.result().isFailed()),
          () -> assertEquals(10, task.result().result())
      );
    }
  }

  @Test
  public void oneTaskFailures() throws InterruptedException{
    try(var scope = new AsyncScope<Object, IOException>()) {
      var task = scope.async(() -> {
        Thread.sleep(100);
        throw new IOException("oops");
      });

      scope.awaitAll();

      assertAll(
          () -> assertThrows(IOException.class, task::getNow),
          () -> assertThrows(IOException.class, () -> task.result().getNow()),
          () -> assertEquals(Result.State.FAILED, task.result().state()),
          () -> assertFalse(task.result().isSuccess()),
          () -> assertTrue(task.result().isFailed()),
          () -> assertTrue(task.result().failure() instanceof IOException)
      );
    }
  }

  @Test
  public void oneTaskResultFailures() throws InterruptedException{
    try(var scope = new AsyncScope<Object, IOException>()) {
      var task = scope.async(() -> {
        Thread.sleep(100);
        throw new IOException("oops");
      });

      scope.awaitAll();

      var result = task.result();
      switch (result.state()) {
        case FAILED -> assertThrows(IOException.class, task::getNow);
        case SUCCESS -> fail();
      }
    }
  }


  @Test
  public void manyTasksSuccess() throws InterruptedException{
    try(var scope = new AsyncScope<Integer, RuntimeException>()) {
      var task = scope.async(() -> {
        Thread.sleep(100);
        return 10;
      });
      var task2 = scope.async(() -> {
        Thread.sleep(300);
        return 30;
      });

      scope.awaitAll();

      int value = task.getNow();
      int value2 = task2.getNow();
      assertEquals(40, value + value2);
    }
  }

  @Test
  public void manyTasksFailure() throws InterruptedException, IOException {
    try(var scope = new AsyncScope<Integer, IOException>()) {
      var task = scope.async(() -> {
        Thread.sleep(100);
        return 10;
      });
      var task2 = scope.async(() -> {
        Thread.sleep(300);
        throw new IOException("oops");
      });

      scope.awaitAll();

      assertEquals(10, task.getNow());
      assertThrows(IOException.class, task2::getNow);
    }
  }


  @Test
  public void manyTasksSuccessStreamToList() throws InterruptedException, IOException {
    try(var scope = new AsyncScope<Integer, RuntimeException>()) {
      var task = scope.async(() -> {
        Thread.sleep(300);
        return 30;
      });
      var task2 = scope.async(() -> {
        Thread.sleep(100);
        return 10;
      });

      List<Integer> values = scope.await(stream -> stream.flatMap(Result::keepOnlySuccess).toList());
      assertEquals(List.of(10, 30), values);
    }
  }


  @Test
  public void manyTasksSuccessShortCircuitStream() throws InterruptedException {
    try(var scope = new AsyncScope<Integer, RuntimeException>()) {
      var task = scope.async(() -> {
        Thread.sleep(100);
        return 10;
      });
      var task2 = scope.async(() -> {
        Thread.sleep(1_000);
        return 30;
      });

      int value = scope.await(stream -> stream.flatMap(Result::keepOnlySuccess).findFirst()).orElseThrow();
      assertEquals(10, value);
      assertEquals(10, task.getNow());
      assertTrue(task2.result().isCancelled());
    }
  }

  @Test
  public void manyTasksFailureShortCircuitStream() throws InterruptedException, IOException {
    try(var scope = new AsyncScope<Integer, IOException>()) {
      var task = scope.async(() -> {
        Thread.sleep(100);
        return 10;
      });
      var task2 = scope.async(() -> {
        Thread.sleep(1_000);
        throw new IOException("oops");
      });

      int value = scope.await(stream -> stream.flatMap(Result::keepOnlySuccess).findFirst()).orElseThrow();
      assertEquals(10, value);
      assertEquals(10, task.getNow());
      assertTrue(task2.result().isCancelled());
    }
  }


  @Test
  public void manyTasksSuccessReduceStream() throws InterruptedException {
    try(var scope = new AsyncScope<Integer, RuntimeException>()) {
      var task = scope.async(() -> {
        Thread.sleep(100);
        return 10;
      });
      var task2 = scope.async(() -> {
        Thread.sleep(300);
        return 30;
      });

      var result = scope.await(stream -> stream.reduce(Result.merger(Integer::sum))).orElseThrow();
      switch (result.state()) {
        case SUCCESS -> assertEquals(40, result.result());
        case FAILED -> fail();
      }
    }
  }

  @Test
  public void manyTasksFailureReduceStream() throws InterruptedException {
    try(var scope = new AsyncScope<Integer, IOException>()) {
      var task = scope.async(() -> {
        Thread.sleep(100);
        return 10;
      });
      var task2 = scope.async(() -> {
        Thread.sleep(300);
        throw new IOException("oops");
      });

      var result = scope.await(stream -> stream.reduce(Result.merger(Integer::sum))).orElseThrow();
      switch (result.state()) {
        case SUCCESS -> assertEquals(10, result.result());
        case FAILED -> fail();
      }
    }
  }

  @Test
  public void manyTasksFailureReduceStream2() throws InterruptedException {
    try(var scope = new AsyncScope<Integer, IOException>()) {
      var task = scope.async(() -> {
        Thread.sleep(300);
        return 10;
      });
      var task2 = scope.async(() -> {
        Thread.sleep(100);
        throw new IOException("oops");
      });

      var result = scope.await(stream -> stream.reduce(Result.merger(Integer::sum))).orElseThrow();
      switch (result.state()) {
        case SUCCESS -> assertEquals(10, result.result());
        case FAILED -> fail();
      }
    }
  }

  @Test
  public void manyTasksAllFailsReduceStream() throws InterruptedException {
    try(var scope = new AsyncScope<Integer, IOException>()) {
      var task = scope.async(() -> {
        Thread.sleep(100);
        throw new IOException("oops");
      });
      var task2 = scope.async(() -> {
        Thread.sleep(300);
        throw new IOException("oops2");
      });

      var result = scope.await(stream -> stream.reduce(Result.merger(Integer::sum))).orElseThrow();
      switch (result.state()) {
        case FAILED -> assertTrue(result.failure() instanceof IOException e && e.getMessage().equals("oops"));
        case SUCCESS -> fail();
      }
    }
  }
}