package fr.umlv.loom.structured;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class StructuredScopeShutdownOnSuccessTest {
  @Test
  public void oneTaskSuccess() throws InterruptedException {
    try(var scope = new StructuredScopeShutdownOnSuccess<Integer, RuntimeException>()) {
      scope.fork(() -> {
        Thread.sleep(100);
        return 42;
      });
      int value = scope.joinAll();
      assertEquals(42, value);
    }
  }

  @Test
  public void oneTaskFailures() throws InterruptedException {
    try(var scope = new StructuredScopeShutdownOnSuccess<Integer, IOException>()) {
      scope.fork(() -> {
        Thread.sleep(100);
        throw new IOException("boom");
      });
      try {
        scope.joinAll();
        fail();
      } catch (IOException e) {
        assertEquals("boom", e.getMessage());
      }
    }
  }

  @Test
  public void oneTaskInterrupted() throws InterruptedException {
    try(var scope = new StructuredScopeShutdownOnSuccess<Void, RuntimeException>()) {
      scope.fork(() -> {
        Thread.sleep(100);
        throw new InterruptedException();
      });
      try {
        scope.joinAll();
        fail();
      } catch(InterruptedException e) {
        // ok
      }
    }
  }

  @Test
  public void noTask() throws InterruptedException {
    try(var scope = new StructuredScopeShutdownOnSuccess<Object, RuntimeException>()) {
      assertThrows(IllegalStateException.class, scope::joinAll);
    }
  }

  @Test
  public void manyTasksSuccess() throws InterruptedException{
    try(var scope = new StructuredScopeShutdownOnSuccess<Integer, RuntimeException>()) {
      scope.fork(() -> {
        Thread.sleep(300);
        return 30;
      });
      scope.fork(() -> {
        Thread.sleep(100);
        return 10;
      });
      int value = scope.joinAll();
      assertEquals(10, value);
    }
  }

  @Test
  public void manyTasksFailure() throws InterruptedException {
    try(var scope = new StructuredScopeShutdownOnSuccess<Integer, IOException>()) {
      scope.fork(() -> {
        Thread.sleep(100);
        throw new IOException("boom");
      });
      scope.fork(() -> {
        Thread.sleep(300);
        throw new IOException("boom2");
      });
      try {
        scope.joinAll();
        fail();
      } catch (IOException e) {
        assertEquals("boom", e.getMessage());
      }
    }
  }

  @Test
  public void manyTasksMixedSuccessFailure() throws InterruptedException, IOException {
    try(var scope = new StructuredScopeShutdownOnSuccess<Integer, IOException>()) {
      scope.fork(() -> {
        Thread.sleep(100);
        throw new IOException("boom");
      });
      scope.fork(() -> {
        Thread.sleep(300);
        return 42;
      });
      int value = scope.joinAll();
      assertEquals(42, value);
    }
  }

  @Test
  public void manyTasksMixedSuccessFailure2() throws InterruptedException, IOException {
    try(var scope = new StructuredScopeShutdownOnSuccess<Integer, IOException>()) {
      scope.fork(() -> {
        Thread.sleep(300);
        throw new IOException("boom");
      });
      scope.fork(() -> {
        Thread.sleep(100);
        return 42;
      });
      int value = scope.joinAll();
      assertEquals(42, value);
    }
  }
}