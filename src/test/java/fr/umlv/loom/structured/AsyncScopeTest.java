package fr.umlv.loom.structured;

import fr.umlv.loom.structured.AsyncScope.Result;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.partitioningBy;
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
  public void manyTasksSuccessCollector() throws InterruptedException{
    try(var scope = new AsyncScope<Integer, RuntimeException>()) {
      var task = scope.async(() -> {
        Thread.sleep(100);
        return 10;
      });
      var task2 = scope.async(() -> {
        Thread.sleep(300);
        return 30;
      });

      var result = scope.await(stream -> stream.collect(Result.toResult(Collectors.toList())));
      assertEquals(List.of(10, 30), result.result());
    }
  }

  @Test
  public void manyTasksFailureCollector() throws InterruptedException{
    try(var scope = new AsyncScope<Integer, IOException>()) {
      var task = scope.async(() -> {
        Thread.sleep(100);
        throw new IOException("oops");
      });
      var task2 = scope.async(() -> {
        Thread.sleep(300);
        throw new IOException("oops2");
      });

      var result = scope.await(stream -> stream.collect(Result.toResult(Collectors.toList())));
      assertTrue(result.failure() instanceof IOException e && e.getMessage().equals("oops"));
    }
  }

  @Test
  public void manyTasksMixedSuccessFailureCollector() throws InterruptedException{
    try(var scope = new AsyncScope<Integer, IOException>()) {
      var task = scope.async(() -> {
        Thread.sleep(100);
        return 10;
      });
      var task2 = scope.async(() -> {
        Thread.sleep(300);
        throw new IOException("oops2");
      });

      var result = scope.await(stream -> stream.collect(Result.toResult(Collectors.toList())));
      assertEquals(List.of(10), result.result());
    }
  }

  @Test
  public void manyTasksMixedFailureSuccessCollector() throws InterruptedException{
    try(var scope = new AsyncScope<Integer, IOException>()) {
      var task = scope.async(() -> {
        Thread.sleep(100);
        throw new IOException("oops");
      });
      var task2 = scope.async(() -> {
        Thread.sleep(300);
        return 30;
      });

      var result = scope.await(stream -> stream.collect(Result.toResult(Collectors.toList())));
      assertEquals(List.of(30), result.result());
    }
  }

  @Test
  public void manyTasksPartition() throws InterruptedException{
    try(var scope = new AsyncScope<Integer, IOException>()) {
      var task = scope.async(() -> {
        Thread.sleep(100);
        throw new IOException("oops");
      });
      var task2 = scope.async(() -> {
        Thread.sleep(300);
        return 30;
      });

      var partition = scope.await(stream -> stream.collect(partitioningBy(Result::isSuccess)));
      assertEquals(List.of(30), partition.get(true).stream().map(Result::result).toList());
      assertEquals(List.of("oops"), partition.get(false).stream().map(r -> r.failure().getMessage()).toList());
    }
  }


  @Test
  public void manyTasksSuccessStreamToResultList() throws InterruptedException, IOException {
    try(var scope = new AsyncScope<Integer, RuntimeException>()) {
      var task = scope.async(() -> {
        Thread.sleep(300);
        return 30;
      });
      var task2 = scope.async(() -> {
        Thread.sleep(100);
        return 10;
      });

      var results = scope.await(Stream::toList);
      var sum = 0;
      for(var result: results) {
        sum += result.getNow();
      }
      assertEquals(40, sum);
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

  // var list = new ArrayList<Result>();
  //  var error = (IOException) null;
  //  for(...) {
  //    Result result;
  //    try {
  //      result = synchronousCall(...);
  //    } catch(UnknownHostException e) {
  //      throw ... (e);
  //    } catch(IOException e) {
  //      if (error == null) {
  //        error = e;  // just record the error
  //      }
  //      continue;
  //    }
  //    if (valid(result)) {
  //      list.add(result);
  //      if (list.size() == 3) {
  //        break;
  //      }
  //    }
  //  }
  //  if (list.isEmpty()) {
  //    throw ... (error);
  //  }
  //  ...
  @Test
  public void complexShortCircuitExample() throws InterruptedException {
    try(var scope = new AsyncScope<Integer, IOException>()) {
      for(var i = 0; i < 30; i++) {
        var id = i;
        scope.async(() -> {
          Thread.sleep(100 + id * 100);
          if (id % 2 == 0) {
            throw new IOException("oops " + id);
            //throw new UnknownHostException("oops");
          }
          return id;
        });
      }
      var box = new Object() { int counter; };
      var result = scope.await(stream -> stream
              .peek(r -> {
                if (r.isFailed() && r.failure() instanceof UnknownHostException e) {
                  throw new UncheckedIOException(e);
                }
              })
              .filter(r -> r.isFailed() || r.result() % 3 == 1)
              .takeWhile(r -> r.isFailed() || box.counter++ < 3)
              .collect(Result.toResult(Collectors.toList())));
      switch (result.state()) {
        case FAILED -> assertTrue(result.failure() instanceof IOException e && e.getMessage().equals("oops 0"));
        case SUCCESS -> assertEquals(List.of(1, 7, 13), result.result());
      }
    }
  }
}