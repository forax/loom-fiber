package fr.umlv.loom;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Generators { 
  /*private*/ static final ContinuationScope SCOPE = new ContinuationScope() { /*empty*/ };

  public static <T> Iterator<T> iterator(Consumer<Consumer<? super T>> consumer) {
    Objects.requireNonNull(consumer);
    return new Iterator<>() {
      private final Continuation continuation = new Continuation(SCOPE, () -> {
        consumer.accept(value -> {
          Objects.requireNonNull(value);
          element = value;
          Continuation.yield(SCOPE);
        });
      });
      private T element;

      @Override
      public boolean hasNext() {
        if (element != null) {
          return true;
        }
        if (continuation.isDone()) {
          return false;
        }
        continuation.run();
        return element != null;
      }

      @Override
      public T next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        var element = this.element;
        this.element = null;
        return element;
      }
    };
  }
  
  public static <T> Stream<T> stream(Consumer<Consumer<? super T>> consumer) {
    Objects.requireNonNull(consumer);
    return StreamSupport.stream(new Spliterator<T>() {
      private final Continuation continuation = new Continuation(SCOPE, () -> {
        consumer.accept(value -> {
          Objects.requireNonNull(value);
          this.action.accept(value);
          Continuation.yield(SCOPE);
        });
      });
      private Consumer<? super T> action;

      @Override
      public Spliterator<T> trySplit() {
        return null;
      }
      @Override
      public boolean tryAdvance(Consumer<? super T> action) {
        if (continuation.isDone()) {
          return false;
        }
        this.action = action;
        try {
          continuation.run();
        } finally {
          this.action = null;
        }
        return true;
      }
      @Override
      public int characteristics() {
        return IMMUTABLE | NONNULL;
      }
      @Override
      public long estimateSize() {
        return Long.MAX_VALUE;
      }
    }, false);
  }
}
