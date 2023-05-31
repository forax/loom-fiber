package fr.umlv.loom.structured;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class StructuredScopeShutdownOnFailureTest {
  @Test
  public void oneTaskSuccess() throws InterruptedException {
    try(var scope = new StructuredScopeShutdownOnFailure<RuntimeException>()) {
      var supplier = scope.fork(() -> {
        Thread.sleep(100);
        return 42;
      });
      scope.joinAll();
      int value = supplier.get();
      assertEquals(42, value);
    }
  }

  @Test
  public void oneTaskFailures() throws InterruptedException {
    try(var scope = new StructuredScopeShutdownOnFailure<IOException>()) {
      var supplier = scope.fork(() -> {
        Thread.sleep(100);
        throw new IOException("boom");
      });
      try {
        scope.joinAll();
        fail();
      } catch (IOException e) {
        assertEquals("boom", e.getMessage());
      }
      assertThrows(IllegalStateException.class, supplier::get);
    }
  }

  @Test
  public void oneTaskInterrupted() {
    try(var scope = new StructuredScopeShutdownOnFailure<RuntimeException>()) {
      var supplier = scope.fork(() -> {
        Thread.sleep(100);
        throw new InterruptedException();
      });
      try {
        scope.joinAll();
        fail();
      } catch(InterruptedException e) {
        // ok
      }

      assertThrows(IllegalStateException.class, supplier::get);
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
      var supplier1 = scope.fork(() -> {
        Thread.sleep(300);
        return 30;
      });
      var supplier2 = scope.fork(() -> {
        Thread.sleep(100);
        return 10;
      });
      scope.joinAll();
      int value = supplier1.get();
      int value2 = supplier2.get();
      assertAll(
          () -> assertEquals(30, value),
          () -> assertEquals(10, value2)
      );
    }
  }

  @Test
  public void manyTasksFailure() throws InterruptedException {
    try(var scope = new StructuredScopeShutdownOnFailure<IOException>()) {
      var supplier = scope.fork(() -> {
        Thread.sleep(100);
        throw new IOException("boom");
      });
      var supplier2 = scope.fork(() -> {
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
          () -> assertThrows(IllegalStateException.class, supplier::get),
          () -> assertThrows(IllegalStateException.class, supplier2::get)
      );
    }
  }

  @Test
  public void manyTasksMixedSuccessFailure() throws InterruptedException {
    try(var scope = new StructuredScopeShutdownOnFailure<IOException>()) {
      var supplier = scope.fork(() -> {
        Thread.sleep(300);
        throw new IOException("boom");
      });
      var supplier2 = scope.fork(() -> {
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
          () -> assertThrows(IllegalStateException.class, supplier::get),
          () -> assertEquals(42, supplier2.get())
      );
    }
  }
}