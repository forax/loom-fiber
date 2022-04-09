package fr.umlv.loom.monad;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

public class AsyncLoopsTest {

  @Test
  public void example() throws InterruptedException, IOException {
    Optional<Path> lastModifiedFile;
    try(var paths = Files.list(Path.of("."))) {
      record PathAndTime(Path path, FileTime time) {}
      lastModifiedFile = AsyncLoops.asyncLoop(paths,
          path -> {
            var time = Files.getLastModifiedTime(path);
            return new PathAndTime(path, time);
          },
          stream -> stream.max(Comparator.comparing(PathAndTime::time)))
          .map(PathAndTime::path);
    }
    //System.out.println(lastModifiedFile);
    assertNotNull(lastModifiedFile);
  }

  @Test
  public void asyncLoop() throws InterruptedException {
    var times = Stream.of(20, 10);
    var list = AsyncLoops.asyncLoop(times,
        time -> {
          Thread.sleep(time);
          return time;
        },
        Stream::toList);
    assertEquals(List.of(20, 10), list);
  }

  @Test
  public void asyncLoopRandom() throws InterruptedException {
    var times = new Random(0)
        .ints(10, 100, 500)
        .boxed()
        .toList()  // Random.ints() does not declare that ints are ordered
        .stream();
    var list = AsyncLoops.asyncLoop(times,
        time -> {
          Thread.sleep(time);
          return time;
        },
        Stream::toList);
    assertEquals(List.of(260, 448, 129, 147, 415, 353, 191, 261, 419, 154), list);
  }

  @Test
  public void asyncLoopFindFirst() throws InterruptedException {
    var times = Stream.of(20, 10);
    var result = AsyncLoops.asyncLoop(times,
        time -> {
          Thread.sleep(time);
          return time;
        },
        Stream::findFirst).orElseThrow();
    assertEquals(20, result);
  }

  @Test
  public void asyncLoopUnorderedFindFirst() throws InterruptedException {
    var times = Stream.of(500, 100).unordered();
    var result = AsyncLoops.asyncLoop(times,
        time -> {
          Thread.sleep(time);
          return time;
        },
        Stream::findFirst).orElseThrow();
    assertEquals(100, result);
  }

  @Test
  public void asyncLoopWithException()  {
    var values = Stream.of(1);
    assertThrows(IOException.class, () -> {
      AsyncLoops.asyncLoop(values,
          value -> {
            throw new IOException("boom");
          },
          Stream::findFirst).orElseThrow();
      });
  }

  @Test
  public void asyncLoopUnorderedWithException()  {
    var values = Stream.of(1).unordered();
    assertThrows(IOException.class, () -> {
      AsyncLoops.asyncLoop(values,
          value -> {
            throw new IOException("boom");
          },
          Stream::findFirst).orElseThrow();
    });
  }

  @Test
  public void asyncLoopWithDeadline() {
    var values = Stream.of(1);
    assertThrows(TimeoutException.class, () -> {
      AsyncLoops.asyncLoopWithDeadline(values,
          value -> {
            Thread.sleep(1_000);
            return null;
          },
          Stream::findFirst,
          Instant.now().plus(100, ChronoUnit.MILLIS)
      ).orElseThrow();
      });
  }

  @Test
  public void asyncLoopUnorderedWithDeadline() {
    var values = Stream.of(1).unordered();
    assertThrows(TimeoutException.class, () -> {
      AsyncLoops.asyncLoopWithDeadline(values,
          value -> {
            Thread.sleep(1_000);
            return null;
          },
          Stream::findFirst,
          Instant.now().plus(100, ChronoUnit.MILLIS)
      ).orElseThrow();
    });
  }

  @Test
  public void asyncLoopEmptyStream() throws InterruptedException {
    var values = Stream.of();
    var list = AsyncLoops.asyncLoop(values,
        value -> fail(),
        Stream::toList);
    assertEquals(List.of(), list);
  }

  @Test
  public void asyncLoopUnorderedEmptyStream() throws InterruptedException {
    var values = Stream.of().unordered();
    var list = AsyncLoops.asyncLoop(values,
        value -> fail(),
        Stream::toList);
    assertEquals(List.of(), list);
  }

  @Test
  public void asyncLoopPreconditions() {
    assertAll(
        () -> assertThrows(NullPointerException.class, () -> AsyncLoops.asyncLoop(null, value -> fail(), stream -> fail())),
        () -> assertThrows(NullPointerException.class, () -> AsyncLoops.asyncLoop(Stream.of(1), null, stream -> fail())),
        () -> assertThrows(NullPointerException.class, () -> AsyncLoops.asyncLoop(Stream.of(1), value -> fail(), null))
    );
  }

  @Test
  public void asyncLoopWithDeadlinePreconditions() {
    assertAll(
        () -> assertThrows(NullPointerException.class, () -> AsyncLoops.asyncLoopWithDeadline(null, value -> fail(), stream -> fail(), Instant.now())),
        () -> assertThrows(NullPointerException.class, () -> AsyncLoops.asyncLoopWithDeadline(Stream.of(1), null, stream -> fail(), Instant.now())),
        () -> assertThrows(NullPointerException.class, () -> AsyncLoops.asyncLoopWithDeadline(Stream.of(1), value -> fail(), null, Instant.now())),
        () -> assertThrows(NullPointerException.class, () -> AsyncLoops.asyncLoopWithDeadline(Stream.of(1), value -> fail(), stream -> fail(), null))
    );
  }
}