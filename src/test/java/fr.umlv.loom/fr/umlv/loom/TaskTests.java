package fr.umlv.loom;

import static fr.umlv.loom.Task.async;

import org.junit.jupiter.api.Test;

@SuppressWarnings("static-method")
class TaskTests {
  @Test
  void asyncAwait() {
    var task = async(() -> sleep(1_000));
    var task2 = async(() -> sleep(1_000));
    System.out.println("task1 elapsed time: " + task.await() + " task2 elapsed time: " + task2.await());
  }

  private static long sleep(int millis) {
    var start = System.nanoTime();
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }
    var end = System.nanoTime();
    return end - start;
  }
}
