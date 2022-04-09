package fr.umlv.loom.monad;

import fr.umlv.loom.monad.AsyncStreams.DeadlineException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

public class AsyncStreamsTest {

  @Test
  public void async() throws InterruptedException {
    var times = Stream.of(20, 10);
    var list = AsyncStreams.async(times,
        time -> {
          Thread.sleep(time);
          return time;
        },
        Stream::toList);
    assertEquals(List.of(20, 10), list);
  }

  @Test
  public void asyncRandom() throws InterruptedException {
    var times = new Random(0)
        .ints(10, 100, 500)
        .boxed()
        .toList()  // Random.ints() does not declare that ints are ordered
        .stream();
    var list = AsyncStreams.async(times,
        time -> {
          Thread.sleep(time);
          return time;
        },
        Stream::toList);
    assertEquals(List.of(260, 448, 129, 147, 415, 353, 191, 261, 419, 154), list);
  }

  @Test
  public void asyncFindFirst() throws InterruptedException {
    var times = Stream.of(20, 10);
    var result = AsyncStreams.async(times,
        time -> {
          Thread.sleep(time);
          return time;
        },
        Stream::findFirst).orElseThrow();
    assertEquals(20, result);
  }

  @Test
  public void asyncUnorderedFindFirst() throws InterruptedException {
    var times = Stream.of(500, 100).unordered();
    var result = AsyncStreams.async(times,
        time -> {
          Thread.sleep(time);
          return time;
        },
        Stream::findFirst).orElseThrow();
    assertEquals(100, result);
  }

  @Test
  public void asyncWithException()  {
    var values = Stream.of(1);
    assertThrows(IOException.class, () -> {
      AsyncStreams.async(values,
          value -> {
            throw new IOException("boom");
          },
          Stream::findFirst).orElseThrow();
      });
  }
  @Test
  public void asyncWithDeadline() {
    var values = Stream.of(1);
    assertThrows(DeadlineException.class, () -> {
      AsyncStreams.asyncWithDeadline(values,
          Instant.now().plus(100, ChronoUnit.MILLIS),
          value -> {
            Thread.sleep(1_000);
            return null;
          },
          Stream::findFirst).orElseThrow();
      });
  }

  @Test
  public void asyncEmptyStream() throws InterruptedException {
    var values = Stream.of();
    var list = AsyncStreams.async(values,
        value -> fail(),
        Stream::toList);
    assertEquals(List.of(), list);
  }

  @Test
  public void asyncInfiniteStream() {
    var values = Stream.iterate(0, x -> x + 1);
    assertThrows(IllegalStateException.class, () -> {
      AsyncStreams.async(values,
          value -> fail(),
          stream -> fail());
    });
  }

  @Test
  public void asyncPreconditions() {
    assertAll(
        () -> assertThrows(NullPointerException.class, () -> AsyncStreams.async(null, value -> fail(), stream -> fail())),
        () -> assertThrows(NullPointerException.class, () -> AsyncStreams.async(Stream.of(1), null, stream -> fail())),
        () -> assertThrows(NullPointerException.class, () -> AsyncStreams.async(Stream.of(1), value -> fail(), null))
    );
  }
}