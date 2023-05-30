package fr.umlv.loom.example;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

// $JAVA_HOME/bin/java --enable-preview --add-modules jdk.incubator.concurrent -cp target/loom-1.0-SNAPSHOT.jar  fr.umlv.loom.example._15_adhoc_scope
// docker run -it --rm --user forax -v /Users/forax:/home/forax -w /home/forax/git/loom-fiber fedora $JAVA_HOME/bin/java --enable-preview -cp target/classes fr.umlv.loom.example._15_adhoc_scope
public interface _15_adhoc_scope {
  sealed interface Result<T> { }
  record Success<T>(T value) implements Result<T> {}
  record Failure<T>(Throwable context) implements Result<T> {}

  class StreamStructuredTaskScope<T> extends StructuredTaskScope<T> {
    private static final Object POISON = new Object();
    private final BlockingQueue<Object> queue = new LinkedBlockingQueue<>();

    @Override
    protected void handleComplete(Subtask<? extends T> future) {
      try {
        queue.put(switch (future.state()) {
          case SUCCESS -> new Success<>(future.get());
          case FAILED -> new Failure<>(future.exception());
          case UNAVAILABLE -> throw new AssertionError();
        });
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    @Override
    public StreamStructuredTaskScope<T> join() throws InterruptedException {
      try {
        super.join();
        return this;
      } finally {
        queue.put(POISON);
        shutdown();
      }
    }

    @Override
    public StreamStructuredTaskScope<T> joinUntil(Instant deadline) throws InterruptedException, TimeoutException {
      try {
        super.joinUntil(deadline);
        return this;
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
      var task1 = scope.fork(() -> {
        Thread.sleep(50);
        return 1;
      });
      var task2 = scope.fork(() -> {
        Thread.sleep(50);
        throw new IOException("oops");
      });
      scope.join().stream().forEach(System.out::println);
    }
  }
}
