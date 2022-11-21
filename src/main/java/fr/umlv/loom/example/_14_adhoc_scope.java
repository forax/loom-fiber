package fr.umlv.loom.example;

import jdk.incubator.concurrent.StructuredTaskScope;

import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

// $JAVA_HOME/bin/java --enable-preview --add-modules jdk.incubator.concurrent -cp target/loom-1.0-SNAPSHOT.jar  fr.umlv.loom.example._14_adhoc_scope
public interface _14_adhoc_scope {
  sealed interface Result<T> { }
  record Success<T>(T value) implements Result<T> {}
  record Failure<T>(Throwable context) implements Result<T> {}
  record Cancelled<T>() implements Result<T> {}

  class StreamStructuredTaskScope<T> extends StructuredTaskScope<T> {
    private static final Object POISON = new Object();
    private final BlockingQueue<Object> queue = new LinkedBlockingQueue<>();

    @Override
    protected void handleComplete(Future<T> future) {
      try {
        queue.put(switch (future.state()) {
          case SUCCESS -> new Success<>(future.resultNow());
          case FAILED -> new Failure<>(future.exceptionNow());
          case CANCELLED -> new Cancelled<>();
          case RUNNING -> throw new AssertionError();
        });
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    @Override
    public StructuredTaskScope<T> join() throws InterruptedException {
      try {
        return super.join();
      } finally {
        queue.put(POISON);
        shutdown();
      }
    }

    @Override
    public StructuredTaskScope<T> joinUntil(Instant deadline) throws InterruptedException, TimeoutException {
      try {
        return super.joinUntil(deadline);
      } finally {
        queue.put(POISON);
        shutdown();
      }
    }

    @SuppressWarnings("unchecked")
    public Stream<Result<T>> stream() {
      return Stream.of((Object) null).mapMulti((__, consumer) -> {
        try {
          Object result;
          while((result = queue.take()) != POISON) {
            consumer.accept((Result<T>) result);
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      });
    }
  }

  static void main(String[] args) throws InterruptedException {
    try(var scope = new StreamStructuredTaskScope<>()) {
      var future1 = scope.fork(() -> {
        Thread.sleep(50);
        return 1;
      });
      var future2 = scope.fork(() -> {
        Thread.sleep(50);
        throw new AssertionError("oops");
      });
      scope.join();
      scope.stream().forEach(System.out::println);
    }
  }
}
