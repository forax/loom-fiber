package fr.umlv.loom.continuation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ContinuationTest {

  @Test
  public void startAndYield() {
    var builder = new StringBuilder();
    var continuation = new Continuation(() -> {
      builder.append("continuation -- start\n");
      Continuation.yield();
      builder.append("continuation -- middle\n");
      Continuation.yield();
      builder.append("continuation -- end\n");
    });
    builder.append("main -- before start\n");
    continuation.run();
    builder.append("main -- after start\n");
    builder.append("main -- before start 2\n");
    continuation.run();
    builder.append("main -- after start 2\n");
    builder.append("main -- before start 3\n");
    continuation.run();
    builder.append("main -- after start 3\n");
    assertEquals("""
        main -- before start
        continuation -- start
        main -- after start
        main -- before start 2
        continuation -- middle
        main -- after start 2
        main -- before start 3
        continuation -- end
        main -- after start 3
        """, builder.toString());
  }

  private static String carrierThreadName() {
    var name = Thread.currentThread().toString();
    var index = name.lastIndexOf('@');
    if (index == -1) {
      throw new AssertionError();
    }
    return name.substring(index + 1);
  }

  @Test
  public void startWhenDone() {
    var continuation = new Continuation(() -> {});
    continuation.run();
    assertThrows(IllegalStateException.class, continuation::run);
  }

  @Test
  public void yieldNotBound() {
    assertThrows(IllegalStateException.class, Continuation::yield);
  }
}