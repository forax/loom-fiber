package fr.umlv.loom.monad;

import fr.umlv.loom.monad.OldAsyncStreams.DeadlineException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class OldAsyncStreamsTest {

  @Test
  public void async() throws InterruptedException {
    var times = Stream.of(20, 10);
    try(var stream = OldAsyncStreams.async(times, time -> {
      Thread.sleep(time);
      return time;
    })) {
      assertEquals(List.of(20, 10), stream.toList());
    }
  }

  @Test
  public void asyncRandom() throws InterruptedException {
    var times = new Random(0)
        .ints(10, 100, 500)
        .boxed()
        .toList()  // Random.ints() does not declare that ints are ordered
        .stream();
    try(var stream = OldAsyncStreams.async(times, time -> {
      Thread.sleep(time);
      return time;
    })) {
      assertEquals(List.of(260, 448, 129, 147, 415, 353, 191, 261, 419, 154), stream.toList());
    }
  }

  @Test
  public void asyncFindFirst() throws InterruptedException {
    var times = Stream.of(20, 10);
    try(var stream = OldAsyncStreams.async(times, time -> {
      Thread.sleep(time);
      return time;
    })) {
      assertEquals(20, stream.findFirst().orElseThrow());
    }
  }

  @Test
  public void asyncUnorderedFindFirst() throws InterruptedException {
    var times = Stream.of(500, 100).unordered();
    try(var stream = OldAsyncStreams.async(times, time -> {
      Thread.sleep(time);
      return time;
    })) {
      assertEquals(100, stream.findFirst().orElseThrow());
    }
  }

  @Test
  public void asyncWithException() {
    var values = Stream.of(1);
    assertThrows(IOException.class, () -> {
      try(var stream = OldAsyncStreams.async(values, value -> {
        throw new IOException("boom");
      })
          // IOException typed to be thrown here
      ) {
        stream.findFirst().orElseThrow();  // but is throw here !
      }
    });
  }
  @Test
  public void asyncWithDeadline() {
    var values = Stream.of(1);
    assertThrows(DeadlineException.class, () -> {
      try(var stream = OldAsyncStreams.asyncWithDeadline(values, Instant.now().plus(100, ChronoUnit.MILLIS), value -> {
        Thread.sleep(1_000);
        return null;
      })) {
        stream.findFirst().orElseThrow();
      }
    });
  }

  @Test
  public void asyncEmptyStream() throws InterruptedException {
    var values = Stream.of();
    try(var stream = OldAsyncStreams.async(values, value -> fail())) {
        assertEquals(List.of(), stream.toList());
    }
  }

  @Test
  public void asyncInfiniteStream() {
    var values = Stream.iterate(0, x -> x + 1);
    assertThrows(IllegalStateException.class, () -> {
      try(var stream = OldAsyncStreams.async(values, value -> fail())) {
        fail();
      }
    });
  }

  @Test
  public void asyncPreconditions() {
    assertAll(
        () -> assertThrows(NullPointerException.class, () -> OldAsyncStreams.async(null, value -> fail())),
        () -> assertThrows(NullPointerException.class, () -> OldAsyncStreams.async(Stream.of(1), null))
    );
  }
}