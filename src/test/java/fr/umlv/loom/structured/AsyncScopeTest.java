package fr.umlv.loom.structured;

import fr.umlv.loom.structured.AsyncScope.DeadlineException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.IntStream.range;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class AsyncScopeTest {
  @Test
  public void ordered() throws InterruptedException {
    try(var scope = AsyncScope.<Integer, RuntimeException>ordered()) {
      scope.async(() -> 42);
      assertEquals(List.of(42), scope.await(Stream::toList));
    }
  }

  @Test
  public void orderedWithSleep() throws InterruptedException {
    try(var scope = AsyncScope.<Integer, RuntimeException>ordered()) {
      scope.async(() -> {
        Thread.sleep(200);
        return 10;
      });
      scope.async(() -> 20);
      assertEquals(List.of(10, 20), scope.await(Stream::toList));
    }
  }

  @Test
  public void orderedSimple() throws InterruptedException {
    try(var scope = AsyncScope.<Integer, RuntimeException>ordered()) {
      scope.async(() -> 10);
      scope.async(() -> 20);
      assertEquals(List.of(10, 20), scope.await(Stream::toList));
    }
  }

  @Test
  public void orderedSeveralTasks() throws InterruptedException {
    try(var scope = AsyncScope.<Integer, RuntimeException>ordered()) {
      range(0, 10_000).forEach(i -> scope.async(() -> i));
      assertEquals(49_995_000, (int) scope.await(stream -> stream.mapToInt(v -> v).sum()));
    }
  }

  @Test
  public void orderedEmpty() throws InterruptedException {
    try(var scope = AsyncScope.<Integer, RuntimeException>ordered()) {
      scope.await(stream -> stream.peek(__ -> fail()).findFirst());
    }
  }

  @Test
  public void unordered() throws InterruptedException {
    try(var scope = AsyncScope.<Integer, RuntimeException>unordered()) {
      scope.async(() -> {
        Thread.sleep(500);
        return 500;
      });
      scope.async(() -> {
        Thread.sleep(1);
        return 1;
      });
      assertEquals(List.of(1, 500), scope.await(Stream::toList));
    }
  }

  @Test
  public void unorderedSimple() throws InterruptedException {
    try(var scope = AsyncScope.<Integer, RuntimeException>unordered()) {
      scope.async(() -> {
        Thread.sleep(200);
        return 10;
      });
      scope.async(() -> 20);
      assertEquals(List.of(20, 10), scope.await(Stream::toList));
    }
  }

  @Test
  public void unorderedFindFirst() throws InterruptedException {
    try(var scope = AsyncScope.<Integer, RuntimeException>unordered()) {
      scope.async(() -> {
        Thread.sleep(200);
        return 10;
      });
      scope.async(() -> 20);
      assertEquals(20, scope.await(Stream::findFirst).orElseThrow());
    }
  }

  @Test
  public void unorderedShortcut() throws InterruptedException {
    var box = new Object() { boolean ok; };
    try(var scope = AsyncScope.<Integer, RuntimeException>unordered()) {
      scope.async(() -> {
        try {
          Thread.sleep(1_000);
        } catch (InterruptedException e) {
          box.ok = true;
          throw e;
        }
        throw new AssertionError("fail !");
      });
      scope.async(() -> {
        Thread.sleep(1);
        return 1;
      });
      assertEquals(1, scope.await(Stream::findFirst).orElseThrow());
    }
    assertTrue(box.ok);
  }

  @Test
  public void unorderedEmpty() throws InterruptedException {
    try(var scope = AsyncScope.<Object, RuntimeException>unordered()) {
      scope.await(stream -> stream.peek(__ -> fail()).findFirst());
    }
  }

  @Test
  public void asyncCalledAfterResult() throws InterruptedException {
    try(var scope = AsyncScope.<Object, RuntimeException>ordered()) {
      scope.await(__ -> null);
      assertThrows(IllegalStateException.class, () -> scope.async(() -> null));
    }
  }

  @Test
  public void recoverAndWrapException() {
    try(var scope = AsyncScope.<Integer, IOException>ordered()) {
      scope.async(() -> {
        throw new IOException("boom !");
      });
      assertThrows(UncheckedIOException.class, () -> scope
          .recover(exception -> { throw new UncheckedIOException(exception); })
          .await(Stream::toList));
    }
  }

  @Test
  public void recoverWithAValue() throws InterruptedException {
    try(var scope = AsyncScope.<Integer, IOException>ordered()) {
      scope.async(() -> {
        throw new IOException("boom !");
      });
      scope.async(() -> 1);
      assertEquals(42, (int) scope
          .recover((IOException exception) -> 41)
          .await(stream -> stream.mapToInt(v -> v).sum()));
    }
  }

  @Test
  public void recoverCanNotRecoverRuntimeExceptions() {
    try(var scope = AsyncScope.ordered()) {
      scope.async(() -> {
        throw new RuntimeException("boom !");
      });
      assertThrows(RuntimeException.class, () ->  scope
          .recover(exception -> fail())  // should not be called
          .await(stream -> stream.peek(__ -> fail()).findFirst()));
    }
  }

  @Test
  public void recoverPrecondition() {
    try(var scope = AsyncScope.ordered()) {
      assertThrows(NullPointerException.class, () -> scope.recover(null));
    }
  }

  @Test
  public void recoverSpecifiedTwice() {
    try(var scope = AsyncScope.ordered()) {
      assertThrows(IllegalStateException.class, () -> scope
          .recover(exception -> null)
          .recover(exception -> null));
    }
  }

  @Test
  public void recoverCalledAfterAwait() throws Exception {
    try(var scope = AsyncScope.ordered()) {
      scope.await(__ -> null);
      assertThrows(IllegalStateException.class, () -> scope.recover(__ -> null));
    }
  }

  @Test
  public void deadline() {
    try(var scope = AsyncScope.<Integer, RuntimeException>unordered()) {
      scope.async(() -> {
        Thread.sleep(5_000);
        throw new AssertionError("fail !");
      });
      assertThrows(DeadlineException.class, () -> scope
          .deadline(Instant.now().plus(100, ChronoUnit.MILLIS))
          .await(stream -> stream.peek(__ -> fail()).toList()));
    }
  }

  @Test
  public void deadlineLongDeadline() throws InterruptedException {
    try(var scope = AsyncScope.<Integer, RuntimeException>unordered()) {
      scope.async(() -> {
        Thread.sleep(1);
        return 1;
      });
      assertEquals(1, scope
          .deadline(Instant.now().plus(1_000, ChronoUnit.MILLIS))
          .await(Stream::findFirst).orElseThrow());
    }
  }
  @Test
  public void deadlineUnordered() {
    try(var scope = AsyncScope.<Integer, RuntimeException>unordered()) {
      scope.async(() -> {
        Thread.sleep(5_000);
        throw new AssertionError("fail !");
      });
      assertThrows(DeadlineException.class, () -> scope
          .deadline(Instant.now().plus(100, ChronoUnit.MILLIS))
          .await(stream -> stream.peek(__ -> fail()).toList()));
    }
  }

  @Test
  public void deadlineUnorderedLongDeadline() throws InterruptedException {
    try(var scope = AsyncScope.<Integer, RuntimeException>unordered()) {
      scope.async(() -> {
        Thread.sleep(1);
        return 1;
      });
      assertEquals(1,scope
          .deadline(Instant.now().plus(1_000, ChronoUnit.MILLIS))
          .await(Stream::findFirst).orElseThrow());
    }
  }

  @Test
  public void deadlinePrecondition() {
    try(var scope = AsyncScope.ordered()) {
      assertThrows(NullPointerException.class, () -> scope.deadline(null));
    }
  }

  @Test
  public void deadlineSpecifiedTwice() {
    try(var scope = AsyncScope.ordered()) {
      assertThrows(IllegalStateException.class, () -> scope
          .deadline(Instant.now())
          .deadline(Instant.now()));
    }
  }

  @Test
  public void deadlineCalledAfterResult() throws InterruptedException {
    try(var scope = AsyncScope.<Integer, RuntimeException>ordered()) {
      scope.await(__ -> null);
      assertThrows(IllegalStateException.class, () -> scope.deadline(Instant.now()));
    }
  }

  @Test
  public void await() throws InterruptedException {
    try(var scope = AsyncScope.<Integer, RuntimeException>ordered()) {
      scope.async(() -> {
        Thread.sleep(1);
        return 1;
      });
      assertEquals(List.of(1), scope.await(Stream::toList));
    }
  }

  @Test
  public void awaitWithNullResult() throws InterruptedException {
    try(var scope = AsyncScope.<Object, RuntimeException>ordered()) {
      scope.async(() -> null);
      assertNull(scope.await(Stream::toList).get(0));
    }
  }

  @Test
  public void awaitShortcut() throws InterruptedException {
    var box = new Object() { boolean ok; };
    try(var scope = AsyncScope.<Integer, RuntimeException>ordered()) {
      scope.async(() -> {
        Thread.sleep(1);
        return 1;
      });
      scope.async(() -> {
        try {
          Thread.sleep(1_000);
        } catch (InterruptedException e) {
          box.ok = true;
          throw e;
        }
        throw new AssertionError("fail !");
      });
      assertEquals(1, scope.await(Stream::findFirst).orElseThrow());
    }
    assertTrue(box.ok);
  }

  @Test
  public void awaitPrecondition() {
    try(var scope = AsyncScope.ordered()) {
      assertThrows(NullPointerException.class, () -> scope.await(null));
    }
  }

  @Test
  public void awaitCalledTwice() throws InterruptedException {
    try(var scope = AsyncScope.<Void, RuntimeException>ordered()) {
      scope.await(__ -> null);
      assertThrows(IllegalStateException.class, () -> scope.await(__ -> null));
    }
  }

  @Test
  public void close() {
    var box = new Object() { boolean ok; };
    try(var scope = AsyncScope.ordered()) {
      scope.async(() -> {
        try {
          Thread.sleep(1_000);
        } catch (InterruptedException e) {
          box.ok = true;
          throw e;
        }
        throw new AssertionError("fail !");
      });

      // do nothing
    }
    assertTrue(box.ok);
  }

  @Test
  public void closeCalledTwice() {
    var scope = AsyncScope.ordered();
    scope.close();
    scope.close();
  }

  @Test
  public void fullExample() throws InterruptedException {
    List<Integer> list;
    try(var scope = AsyncScope.<Integer, IOException>unordered()) {
      scope.async(() -> {
        Thread.sleep(400);
        return 111;
      });
      scope.async(() -> {
        Thread.sleep(200);
        throw new IOException("boom !");
      });
      scope.async(() -> 666);
      list = scope
          .recover(ioException -> 333)
          .deadline(Instant.now().plus(1, ChronoUnit.SECONDS))
          .await(Stream::toList);
    }
    assertEquals(List.of(666, 333, 111), list);
  }
}