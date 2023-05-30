package fr.umlv.loom.structured;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class StructuredScopeShutdownOnFailureTest {
  @Test
  public void oneTaskSuccess() throws InterruptedException {
    try(var scope = new StructuredScopeShutdownOnFailure<RuntimeException>()) {
      var handle = scope.fork(() -> {
        Thread.sleep(100);
        return 42;
      });
      scope.joinAll();
      int value = handle.get();
      assertAll(
          () -> assertEquals(StructuredScopeShutdownOnFailure.TaskHandle.State.SUCCESS, handle.state()),
          () -> assertEquals(42, value)
      );
    }
  }

  @Test
  public void oneTaskFailures() throws InterruptedException {
    try(var scope = new StructuredScopeShutdownOnFailure<IOException>()) {
      var handle = scope.fork(() -> {
        Thread.sleep(100);
        throw new IOException("boom");
      });
      try {
        scope.joinAll();
        fail();
      } catch (IOException e) {
        assertEquals("boom", e.getMessage());
      }
      assertAll(
          () -> assertEquals(StructuredScopeShutdownOnFailure.TaskHandle.State.FAILED, handle.state()),
          () -> assertThrows(IllegalStateException.class, handle::get)
      );
    }
  }

  @Test
  public void oneTaskInterrupted() throws InterruptedException {
    try(var scope = new StructuredScopeShutdownOnFailure<RuntimeException>()) {
      var handle = scope.fork(() -> {
        Thread.sleep(100);
        throw new InterruptedException();
      });
      try {
        scope.joinAll();
        fail();
      } catch(InterruptedException e) {
        // ok
      }

      assertAll(
          () -> assertEquals(StructuredScopeShutdownOnFailure.TaskHandle.State.UNAVAILABLE, handle.state()),
          () -> assertThrows(IllegalStateException.class, handle::get)
      );
    }
  }

  @Test
  public void noTask() throws InterruptedException {
    try(var scope = new StructuredScopeShutdownOnFailure<RuntimeException>()) {
      scope.joinAll();
    }
  }

  @Test
  public void manyTasksSuccess() throws InterruptedException{
    try(var scope = new StructuredScopeShutdownOnFailure<RuntimeException>()) {
      var handle = scope.fork(() -> {
        Thread.sleep(300);
        return 30;
      });
      var handle2 = scope.fork(() -> {
        Thread.sleep(100);
        return 10;
      });
      scope.joinAll();
      int value = handle.get();
      int value2 = handle2.get();
      assertAll(
          () -> assertEquals(StructuredScopeShutdownOnFailure.TaskHandle.State.SUCCESS, handle.state()),
          () -> assertEquals(30, value),
          () -> assertEquals(StructuredScopeShutdownOnFailure.TaskHandle.State.SUCCESS, handle2.state()),
          () -> assertEquals(10, value2)
      );
    }
  }

  @Test
  public void manyTasksFailure() throws InterruptedException {
    try(var scope = new StructuredScopeShutdownOnFailure<IOException>()) {
      var handle = scope.fork(() -> {
        Thread.sleep(100);
        throw new IOException("boom");
      });
      var handle2 = scope.fork(() -> {
        Thread.sleep(300);
        throw new IOException("boom2");
      });
      try {
        scope.joinAll();
        fail();
      } catch (IOException e) {
        assertEquals("boom", e.getMessage());
      }
      assertAll(
          () -> assertEquals(StructuredScopeShutdownOnFailure.TaskHandle.State.FAILED, handle.state()),
          () -> assertThrows(IllegalStateException.class, handle::get),
          () -> assertEquals(StructuredScopeShutdownOnFailure.TaskHandle.State.UNAVAILABLE, handle2.state()),
          () -> assertThrows(IllegalStateException.class, handle2::get)
      );
    }
  }

  @Test
  public void manyTasksMixedSuccessFailure() throws InterruptedException {
    try(var scope = new StructuredScopeShutdownOnFailure<IOException>()) {
      var handle = scope.fork(() -> {
        Thread.sleep(300);
        throw new IOException("boom");
      });
      var handle2 = scope.fork(() -> {
        Thread.sleep(100);
        return 42;
      });
      try {
        scope.joinAll();
        fail();
      } catch (IOException e) {
        assertEquals("boom", e.getMessage());
      }
      assertAll(
          () -> assertEquals(StructuredScopeShutdownOnFailure.TaskHandle.State.FAILED, handle.state()),
          () -> assertThrows(IllegalStateException.class, handle::get),
          () -> assertEquals(StructuredScopeShutdownOnFailure.TaskHandle.State.SUCCESS, handle2.state()),
          () -> assertEquals(42, handle2.get())
      );
    }
  }
}