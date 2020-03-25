package fr.umlv.loom;

import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@SuppressWarnings("static-method")
@Disabled
public class GeneratorsTests {
  @Test
  public void iteratorSimple() {
    assertAll(
        () -> assertFalse(Generators.iterator(consumer -> { /* */}).hasNext()),
        () -> assertTrue(Generators.iterator(consumer -> { consumer.accept("foo");}).hasNext()),
        () -> assertEquals("bar", Generators.iterator(consumer -> { consumer.accept("bar");}).next()),
        () -> {
          var it = Generators.iterator(consumer -> { consumer.accept("booz"); consumer.accept("baz");});
          assertTrue(it.hasNext());
          assertEquals("booz", it.next());
          assertTrue(it.hasNext());
          assertEquals("baz", it.next());
          assertFalse(it.hasNext());
        }
      );
  }
  
  @Test
  public void iteratorWithALotOfObject() {
    var it = Generators.<Integer>iterator(consumer -> { range(0, 1_000).forEach(consumer::accept); });
    var list = new ArrayList<Integer>();
    it.forEachRemaining(list::add);
    assertEquals(range(0, 1_000).boxed().collect(toList()), list);
  }
  
  @Test
  public void streamSimple() {
    assertAll(
        () -> assertFalse(Generators.stream(consumer -> { /* */}).findFirst().isPresent()),
        () -> assertTrue(Generators.stream(consumer -> { consumer.accept("foo");}).findFirst().isPresent()),
        () -> assertEquals("bar", Generators.stream(consumer -> { consumer.accept("bar");}).findFirst().orElseThrow()),
        () -> {
          var stream = Generators.<String>stream(consumer -> { consumer.accept("booz"); consumer.accept("baz");});
          assertEquals(List.of("booz", "baz"), stream.collect(toList()));
        }
      );
  }
  
  @Test
  public void streamWithALotOfObject() {
    var stream = Generators.<Integer>stream(consumer -> { range(0, 1_000).forEach(consumer::accept); });
    assertEquals(range(0, 1_000).boxed().collect(toList()), stream.collect(toList()));
  }
}
