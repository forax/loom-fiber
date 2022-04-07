package fr.umlv.loom.monad;

import fr.umlv.loom.monad.AsyncMonad.DeadlineException;
import fr.umlv.loom.monad.AsyncMonad.Task;
import fr.umlv.loom.monad.AsyncMonad.TaskForker;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.IntStream.range;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class AsyncMonadTest {
  @Test
  public void ofForker() throws Exception {
    try(var asyncMonad = AsyncMonad.of(taskForker -> {
      taskForker.fork(() -> 42);
    })) {
      assertEquals(List.of(42), asyncMonad.result(Stream::toList));
    }
  }

  @Test
  public void ofForkerSeveralTasks() throws InterruptedException {
    try(var asyncMonad = AsyncMonad.<Integer, RuntimeException>of(taskForker -> {
      range(0, 10_000).forEach(i -> taskForker.fork(() -> i));
    })) {
      assertEquals(49_995_000, (int) asyncMonad.result(stream -> stream.mapToInt(v -> v).sum()));
    }
  }

  @Test
  public void ofForkerEmpty() throws Exception {
    try(var asyncMonad = AsyncMonad.of(taskForker -> {})) {
      asyncMonad.result(stream -> stream.peek(__ -> fail()).findFirst());
    }
  }

  @Test
  public void ofForkerPrecondition() {
    assertThrows(NullPointerException.class, () -> AsyncMonad.of((Consumer<TaskForker<Object, RuntimeException>>) null));
  }

  @Test
  public void ofOrderedCollection() throws InterruptedException {
    var list = List.<Task<Integer, RuntimeException>>of(() -> 42);
    try(var asyncMonad = AsyncMonad.of(list)) {
      assertEquals(List.of(42), asyncMonad.result(Stream::toList));
    }
  }

  @Test
  public void ofOrderedStreamCharacteristic() throws InterruptedException {
    var list = List.<Task<Integer, RuntimeException>>of(() -> 42);
    try(var asyncMonad = AsyncMonad.of(list)) {
      assertTrue( (boolean) asyncMonad.result(stream -> stream.spliterator().hasCharacteristics(Spliterator.ORDERED | Spliterator.SIZED | Spliterator.IMMUTABLE )));
    }
  }

  @Test
  public void ofOrderedCollectionSeveralTasks() throws InterruptedException {
    var list = range(0, 10_000).<Task<Integer, RuntimeException>>mapToObj(i -> () -> i).toList();
    try(var asyncMonad = AsyncMonad.of(list)) {
      assertEquals(49_995_000, (int) asyncMonad.result(stream -> stream.mapToInt(v -> v).sum()));
    }
  }

  @Test
  public void ofUnorderedCollection() throws InterruptedException {
    var set = Set.<Task<Integer, RuntimeException>>of(() -> 42);
    try(var asyncMonad = AsyncMonad.of(set)) {
      assertEquals(Set.of(42), asyncMonad.result(stream -> stream.collect(Collectors.toUnmodifiableSet())));
    }
  }

  @Test
  public void ofUnorderedStreamCharacteristic() throws InterruptedException {
    var set = Set.<Task<Integer, RuntimeException>>of(() -> 42);
    try(var asyncMonad = AsyncMonad.of(set)) {
      assertTrue( (boolean) asyncMonad.result(stream -> stream.spliterator().hasCharacteristics(Spliterator.SIZED | Spliterator.IMMUTABLE)));
    }
  }

  @Test
  public void ofUnorderedCollectionSeveralTasks() throws InterruptedException {
    var set = range(0, 10_000).<Task<Integer, RuntimeException>>mapToObj(i -> () -> i).collect(Collectors.toUnmodifiableSet());
    try(var asyncMonad = AsyncMonad.of(set)) {
      assertEquals(49_995_000, (int) asyncMonad.result(stream -> stream.mapToInt(v -> v).sum()));
    }
  }

  @Test
  public void ofCollectionEmpty() throws Exception {
    try(var asyncMonad = AsyncMonad.of(List.of())) {
      asyncMonad.result(stream -> stream.peek(__ -> fail()).findFirst());
    }
  }

  @Test
  public void ofCollectionPrecondition() {
    assertThrows(NullPointerException.class, () -> AsyncMonad.of((List<Task<Object,RuntimeException>>) null));
  }

  @Test
  public void unordered() throws InterruptedException {
    try(var asyncMonad = AsyncMonad.<Integer, RuntimeException>of(taskForker -> {
      taskForker.fork(() -> {
        Thread.sleep(500);
        return 500;
      });
      taskForker.fork(() -> {
        Thread.sleep(1);
        return 1;
      });
    })) {
      assertEquals(List.of(1, 500), asyncMonad
          .unordered()
          .result(Stream::toList));
    }
  }

  @Test
  public void unorderedShortcut() throws InterruptedException {
    var box = new Object() { boolean ok; };
    try(var asyncMonad = AsyncMonad.<Integer, RuntimeException>of(taskForker -> {
      taskForker.fork(() -> {
        try {
          Thread.sleep(1_000);
        } catch (InterruptedException e) {
          box.ok = true;
          throw e;
        }
        throw new AssertionError("fail !");
      });
      taskForker.fork(() -> {
        Thread.sleep(1);
        return 1;
      });
    })) {
      assertEquals(1, asyncMonad
          .unordered()
          .result(Stream::findFirst).orElseThrow());
    }
    assertTrue(box.ok);
  }

  @Test
  public void recoverWrapException() {
    try(var asyncMonad = AsyncMonad.<Integer, IOException>of(taskForker -> {
      taskForker.fork(() -> {
        throw new IOException("boom !");
      });
    })) {
      assertThrows(UncheckedIOException.class,
          () -> asyncMonad
              .recover(exception -> { throw new UncheckedIOException(exception); })
              .result(Stream::toList));
    }
  }

  @Test
  public void recoverWithAValue() throws InterruptedException {
    try(var asyncMonad = AsyncMonad.<Integer, IOException>of(taskForker -> {
      taskForker.fork(() -> {
        throw new IOException("boom !");
      });
      taskForker.fork(() -> 1);
    })) {
      assertEquals(42, (int) asyncMonad
          .recover(exception -> 41)
          .result(stream -> stream.mapToInt(v -> v).sum()));
    }
  }

  @Test
  public void recoverCanNotRecoverRuntimeExceptions() {
    try(var asyncMonad = AsyncMonad.of(taskForker -> {
      taskForker.fork(() -> {
        throw new RuntimeException("boom !");
      });
    })) {
      assertThrows(RuntimeException.class, () ->  asyncMonad
          .recover(exception -> fail())  // should not be called
          .result(stream -> stream.peek(__ -> fail()).findFirst()));
    }
  }

  @Test
  public void recoverPrecondition() {
    try(var asyncMonad = AsyncMonad.of(taskForker -> {})) {
      assertThrows(NullPointerException.class, () -> asyncMonad.recover(null));
    }
  }

  @Test
  public void recoverSpecifiedTwice() {
    try(var asyncMonad = AsyncMonad.of(List.of())) {
      assertThrows(IllegalStateException.class, () -> asyncMonad
          .recover(exception -> null)
          .recover(exception -> null));
    }
  }

  @Test
  public void deadlineForker() {
    try(var asyncMonad = AsyncMonad.<Integer, RuntimeException>of(taskForker -> {
      taskForker.fork(() -> {
        Thread.sleep(5_000);
        throw new AssertionError("fail !");
      });
    })) {
      assertThrows(DeadlineException.class, () -> asyncMonad
          .deadline(Instant.now().plus(100, ChronoUnit.MILLIS))
          .result(stream -> stream.peek(__ -> fail()).toList()));
    }
  }

  @Test
  public void deadlineForkerLongDeadline() throws InterruptedException {
    try(var asyncMonad = AsyncMonad.<Integer, RuntimeException>of(taskForker -> {
      taskForker.fork(() -> {
        Thread.sleep(1);
        return 1;
      });
    })) {
      assertEquals(1,asyncMonad
          .deadline(Instant.now().plus(1_000, ChronoUnit.MILLIS))
          .result(Stream::findFirst).orElseThrow());
    }
  }

  @Test
  public void deadlineOrderedCollection() {
    var list = List.<Task<Integer, RuntimeException>>of(() -> {
      Thread.sleep(5_000);
      throw new AssertionError("fail !");
    });
    try(var asyncMonad = AsyncMonad.of(list)) {
      assertThrows(DeadlineException.class, () -> asyncMonad
          .deadline(Instant.now().plus(100, ChronoUnit.MILLIS))
          .result(stream -> stream.peek(__ -> fail()).toList()));
    }
  }

  @Test
  public void deadlineOrderedCollectionLongDeadline() throws InterruptedException {
    var list = List.<Task<Integer, RuntimeException>>of(() -> {
      Thread.sleep(1);
      return 1;
    });
    try(var asyncMonad = AsyncMonad.of(list)) {
      assertEquals(1,asyncMonad
          .deadline(Instant.now().plus(1_000, ChronoUnit.MILLIS))
          .result(Stream::findFirst).orElseThrow());
    }
  }

  @Test
  public void deadlineUnorderedCollection() {
    var set = Set.<Task<Integer, RuntimeException>>of(() -> {
      Thread.sleep(5_000);
      throw new AssertionError("fail !");
    });
    try(var asyncMonad = AsyncMonad.of(set)) {
      assertThrows(DeadlineException.class, () -> asyncMonad
          .deadline(Instant.now().plus(100, ChronoUnit.MILLIS))
          .result(stream -> stream.peek(__ -> fail()).toList()));
    }
  }

  @Test
  public void deadlineUnorderedCollectionLongDeadline() throws InterruptedException {
    var set = Set.<Task<Integer, RuntimeException>>of(() -> {
      Thread.sleep(1);
      return 1;
    });
    try(var asyncMonad = AsyncMonad.of(set)) {
      assertEquals(1,asyncMonad
          .deadline(Instant.now().plus(1_000, ChronoUnit.MILLIS))
          .result(Stream::findFirst).orElseThrow());
    }
  }

  @Test
  public void deadlinePrecondition() {
    try(var asyncMonad = AsyncMonad.of(taskForker -> {})) {
      assertThrows(NullPointerException.class, () -> asyncMonad.deadline(null));
    }
  }

  @Test
  public void deadlineSpecifiedTwice() {
    try(var asyncMonad = AsyncMonad.of(List.of())) {
      assertThrows(IllegalStateException.class, () -> asyncMonad
          .deadline(Instant.now())
          .deadline(Instant.now()));
    }
  }

  @Test
  public void result() throws InterruptedException {
    try(var asyncMonad = AsyncMonad.<Integer, RuntimeException>of(taskForker -> {
      taskForker.fork(() -> {
        Thread.sleep(1);
        return 1;
      });
    })) {
      assertEquals(List.of(1), asyncMonad.result(Stream::toList));
    }
  }

  @Test
  public void resultWithNull() throws InterruptedException {
    try(var asyncMonad = AsyncMonad.<Integer, RuntimeException>of(taskForker -> {
      taskForker.fork(() -> null);
    })) {
      assertNull(asyncMonad.result(Stream::toList).get(0));
    }
  }

  @Test
  public void resultShortcut() throws InterruptedException {
    var box = new Object() { boolean ok; };
    try(var asyncMonad = AsyncMonad.<Integer, RuntimeException>of(taskForker -> {
      taskForker.fork(() -> {
        Thread.sleep(1);
        return 1;
      });
      taskForker.fork(() -> {
        try {
          Thread.sleep(1_000);
        } catch (InterruptedException e) {
          box.ok = true;
          throw e;
        }
        throw new AssertionError("fail !");
      });
    })) {
      assertEquals(1, asyncMonad.result(Stream::findFirst).orElseThrow());
    }
    assertTrue(box.ok);
  }

  @Test
  public void resultPrecondition() {
    try(var asyncMonad = AsyncMonad.of(taskForker -> {})) {
      assertThrows(NullPointerException.class, () -> asyncMonad.result(null));
    }
  }

  @Test
  public void close() {
    var box = new Object() { boolean ok; };
    try(var asyncMonad = AsyncMonad.of(taskForker -> {
      taskForker.fork(() -> {
        try {
          Thread.sleep(1_000);
        } catch (InterruptedException e) {
          box.ok = true;
          throw e;
        }
        throw new AssertionError("fail !");
      });
    })) {
      // do nothing
    }
    assertTrue(box.ok);
  }
}