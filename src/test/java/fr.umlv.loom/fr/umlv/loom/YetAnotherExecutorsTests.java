package fr.umlv.loom;

import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

@SuppressWarnings("static-method")
class YetAnotherExecutorsTests {
  @Test
  void execute_10_000_fibers() {
    assertTimeout(Duration.of(1, ChronoUnit.SECONDS), () -> {
      var stat =
          range(0, 10_000)
          .<Supplier<Long>>mapToObj(__ -> () -> {
            var start = System.nanoTime();
            sleep(10);
            var end = System.nanoTime();
            return end - start;
          })
          .map(YetAnotherExecutors::execute)
          .collect(toList())
          .stream()
          .mapToLong(t -> {
            try {
              return t.get();
            } catch (InterruptedException | ExecutionException e) {
              throw new AssertionError(e);
            }
          })
          .summaryStatistics();
      System.out.println(stat);
    });
  }

  private static void sleep(int millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }
  }
}
