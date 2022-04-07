package fr.umlv.loom.monad;

import fr.umlv.loom.monad.AsyncScope.DeadlineException;
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
  public void of() throws Exception {
    try(var scope = AsyncScope.of()) {
      scope.fork(() -> 42);
      assertEquals(List.of(42), scope.result(Stream::toList));
    }
  }

  @Test
  public void simple() throws Exception {
    try(var scope = AsyncScope.of()) {
      scope.fork(() -> 10);
      scope.fork(() -> 20);
      assertEquals(List.of(10, 20), scope.result(Stream::toList));
    }
  }

  @Test
  public void ofSeveralTasks() throws InterruptedException {
    try(var scope = AsyncScope.<Integer, RuntimeException>of()) {
      range(0, 10_000).forEach(i -> scope.fork(() -> i));
      assertEquals(49_995_000, (int) scope.result(stream -> stream.mapToInt(v -> v).sum()));
    }
  }

  @Test
  public void ofEmpty() throws Exception {
    try(var scope = AsyncScope.of()) {
      scope.result(stream -> stream.peek(__ -> fail()).findFirst());
    }
  }

  @Test
  public void unordered() throws InterruptedException {
    try(var scope = AsyncScope.<Integer, RuntimeException>unordered()) {
      scope.fork(() -> {
        Thread.sleep(500);
        return 500;
      });
      scope.fork(() -> {
        Thread.sleep(1);
        return 1;
      });
      assertEquals(List.of(1, 500), scope.result(Stream::toList));
    }
  }

  @Test
  public void unorderedSimple() throws Exception {
    try(var scope = AsyncScope.unordered()) {
      scope.fork(() -> {
        Thread.sleep(200);
        return 10;
      });
      scope.fork(() -> 20);
      assertEquals(List.of(20, 10), scope.result(Stream::toList));
    }
  }

  @Test
  public void unorderedShortcut() throws InterruptedException {
    var box = new Object() { boolean ok; };
    try(var scope = AsyncScope.<Integer, RuntimeException>unordered()) {
      scope.fork(() -> {
        try {
          Thread.sleep(1_000);
        } catch (InterruptedException e) {
          box.ok = true;
          throw e;
        }
        throw new AssertionError("fail !");
      });
      scope.fork(() -> {
        Thread.sleep(1);
        return 1;
      });
      assertEquals(1, scope.result(Stream::findFirst).orElseThrow());
    }
    assertTrue(box.ok);
  }

  @Test
  public void unorderedEmpty() throws Exception {
    try(var scope = AsyncScope.unordered()) {
      scope.result(stream -> stream.peek(__ -> fail()).findFirst());
    }
  }

  @Test
  public void forkCalledAterResult() throws Exception {
    try(var scope = AsyncScope.of()) {
      scope.result(__ -> null);
      assertThrows(IllegalStateException.class, () -> scope.fork(() -> null));
    }
  }

  @Test
  public void recoverWrapException() {
    try(var scope = AsyncScope.<Integer, IOException>of()) {
      scope.fork(() -> {
        throw new IOException("boom !");
      });
      assertThrows(UncheckedIOException.class, () -> scope
          .recover(exception -> { throw new UncheckedIOException(exception); })
          .result(Stream::toList));
    }
  }

  @Test
  public void recoverWithAValue() throws InterruptedException {
    try(var scope = AsyncScope.<Integer, IOException>of()) {
      scope.fork(() -> {
        throw new IOException("boom !");
      });
      scope.fork(() -> 1);
      assertEquals(42, (int) scope
          .recover((IOException exception) -> 41)
          .result(stream -> stream.mapToInt(v -> v).sum()));
    }
  }

  @Test
  public void recoverCanNotRecoverRuntimeExceptions() {
    try(var scope = AsyncScope.of()) {
      scope.fork(() -> {
        throw new RuntimeException("boom !");
      });
      assertThrows(RuntimeException.class, () ->  scope
          .recover(exception -> fail())  // should not be called
          .result(stream -> stream.peek(__ -> fail()).findFirst()));
    }
  }

  @Test
  public void recoverPrecondition() {
    try(var scope = AsyncScope.of()) {
      assertThrows(NullPointerException.class, () -> scope.recover(null));
    }
  }

  @Test
  public void recoverSpecifiedTwice() {
    try(var scope = AsyncScope.of()) {
      assertThrows(IllegalStateException.class, () -> scope
          .recover(exception -> null)
          .recover(exception -> null));
    }
  }

  @Test
  public void recoverCalledAfterResult() throws Exception {
    try(var scope = AsyncScope.of()) {
      scope.result(__ -> null);
      assertThrows(IllegalStateException.class, () -> scope.recover(__ -> null));
    }
  }

  @Test
  public void deadline() {
    try(var scope = AsyncScope.<Integer, RuntimeException>unordered()) {
      scope.fork(() -> {
        Thread.sleep(5_000);
        throw new AssertionError("fail !");
      });
      assertThrows(DeadlineException.class, () -> scope
          .deadline(Instant.now().plus(100, ChronoUnit.MILLIS))
          .result(stream -> stream.peek(__ -> fail()).toList()));
    }
  }

  @Test
  public void deadlineLongDeadline() throws InterruptedException {
    try(var scope = AsyncScope.<Integer, RuntimeException>unordered()) {
      scope.fork(() -> {
        Thread.sleep(1);
        return 1;
      });
      assertEquals(1, scope
          .deadline(Instant.now().plus(1_000, ChronoUnit.MILLIS))
          .result(Stream::findFirst).orElseThrow());
    }
  }
  @Test
  public void deadlineUnordered() {
    try(var scope = AsyncScope.unordered()) {
      scope.fork(() -> {
        Thread.sleep(5_000);
        throw new AssertionError("fail !");
      });
      assertThrows(DeadlineException.class, () -> scope
          .deadline(Instant.now().plus(100, ChronoUnit.MILLIS))
          .result(stream -> stream.peek(__ -> fail()).toList()));
    }
  }

  @Test
  public void deadlineUnorderedLongDeadline() throws InterruptedException {
    try(var scope = AsyncScope.<Integer, RuntimeException>unordered()) {
      scope.fork(() -> {
        Thread.sleep(1);
        return 1;
      });
      assertEquals(1,scope
          .deadline(Instant.now().plus(1_000, ChronoUnit.MILLIS))
          .result(Stream::findFirst).orElseThrow());
    }
  }

  @Test
  public void deadlinePrecondition() {
    try(var scope = AsyncScope.of()) {
      assertThrows(NullPointerException.class, () -> scope.deadline(null));
    }
  }

  @Test
  public void deadlineSpecifiedTwice() {
    try(var scope = AsyncScope.of()) {
      assertThrows(IllegalStateException.class, () -> scope
          .deadline(Instant.now())
          .deadline(Instant.now()));
    }
  }

  @Test
  public void deadlineCalledAfterResult() throws Exception {
    try(var scope = AsyncScope.of()) {
      scope.result(__ -> null);
      assertThrows(IllegalStateException.class, () -> scope.deadline(Instant.now()));
    }
  }

  @Test
  public void result() throws InterruptedException {
    try(var scope = AsyncScope.<Integer, RuntimeException>of()) {
      scope.fork(() -> {
        Thread.sleep(1);
        return 1;
      });
      assertEquals(List.of(1), scope.result(Stream::toList));
    }
  }

  @Test
  public void resultWithNull() throws InterruptedException {
    try(var scope = AsyncScope.<Object, RuntimeException>of()) {
      scope.fork(() -> null);
      assertNull(scope.result(Stream::toList).get(0));
    }
  }

  @Test
  public void resultShortcut() throws InterruptedException {
    var box = new Object() { boolean ok; };
    try(var scope = AsyncScope.<Integer, RuntimeException>of()) {
      scope.fork(() -> {
        Thread.sleep(1);
        return 1;
      });
      scope.fork(() -> {
        try {
          Thread.sleep(1_000);
        } catch (InterruptedException e) {
          box.ok = true;
          throw e;
        }
        throw new AssertionError("fail !");
      });
      assertEquals(1, scope.result(Stream::findFirst).orElseThrow());
    }
    assertTrue(box.ok);
  }

  @Test
  public void resultPrecondition() {
    try(var scope = AsyncScope.of()) {
      assertThrows(NullPointerException.class, () -> scope.result(null));
    }
  }

  @Test
  public void resultCalledAfterResult() throws Exception {
    try(var scope = AsyncScope.of()) {
      scope.result(__ -> null);
      assertThrows(IllegalStateException.class, () -> scope.result(__ -> null));
    }
  }

  @Test
  public void close() {
    var box = new Object() { boolean ok; };
    try(var scope = AsyncScope.of()) {
      scope.fork(() -> {
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
}