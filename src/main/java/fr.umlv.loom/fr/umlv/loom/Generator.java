package fr.umlv.loom;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;

public class Generator { 
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
}
