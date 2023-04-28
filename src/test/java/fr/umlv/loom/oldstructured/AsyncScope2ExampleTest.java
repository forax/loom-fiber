package fr.umlv.loom.oldstructured;

import fr.umlv.loom.oldstructured.AsyncScope2;
import fr.umlv.loom.oldstructured.AsyncScope2.DeadlineException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Stream;

public final class AsyncScope2ExampleTest {
  sealed interface TravelItem permits Weather, Quotation {}
  record Weather(String source, String text) implements TravelItem { }
  record Quotation(String source, int price) implements TravelItem {}

  private static final Weather DEFAULT_WEATHER = new Weather("", "Sunny");

  private Quotation quotation() throws InterruptedException {
    var random = new Random();
    try (var quotationScope = AsyncScope2.<Quotation, IOException>unordered()) {
      quotationScope.async(() -> {
        Thread.sleep(random.nextInt(20, 120));
        return new Quotation("QA", random.nextInt(50, 100));
      });
      quotationScope.async(() -> {
        Thread.sleep(random.nextInt(30, 130));
        //return new Quotation("QB", random.nextInt(30, 100));
        throw new IOException("oops !");
      });

      return quotationScope
          .recover(e -> null)  // ignore
          .await(s -> s.filter(Objects::nonNull).min(Comparator.comparing(Quotation::price))).orElseThrow();
    }
  }

  private Weather weather() throws InterruptedException {
    var random = new Random();
    try (var weatherScope = AsyncScope2.<Weather, RuntimeException>unordered()) {
      weatherScope.async(() -> {
        Thread.sleep(random.nextInt(50, 100));
        return new Weather("WA", "Cloudy");
      });
      weatherScope.async(() -> {
        Thread.sleep(random.nextInt(60, 80));
        return new Weather("WB", "Sunny");
      });
      return weatherScope
          .deadline(Instant.now().plus(70, ChronoUnit.MILLIS))
          .await(Stream::findFirst).orElse(DEFAULT_WEATHER);
    } catch (DeadlineException e) {
      return DEFAULT_WEATHER;
    }
  }

  @Test
  public void travelAgency() throws InterruptedException {
    List<TravelItem> travelItems;
    try(var travelScope = AsyncScope2.<TravelItem, RuntimeException>unordered()) {
      travelScope.async(this::quotation);
      travelScope.async(this::weather);

      travelItems = travelScope.await(Stream::toList);
    }

    Quotation quotation = null;
    Weather weather = null;
    for(var travelItem: travelItems) {
      //switch (travelItem) {
      //  case Quotation q -> quotation = q;
      //  case Weather w -> weather = w;
      //}
      if (travelItem instanceof Quotation q) {
        quotation = q;
      } else if (travelItem instanceof Weather w) {
        weather = w;
      } else throw new AssertionError();
    }
    System.out.println("quotation " + quotation + " weather " + weather);
  }
}
