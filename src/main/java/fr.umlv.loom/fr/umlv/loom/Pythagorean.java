package fr.umlv.loom;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

public class Pythagorean {
  static <T> Iterator<T> iterator(Consumer<Consumer<? super T>> consumer) {
    return new Iterator<>() {
      private T value;
      private final Continuation continuation;
      
      {
        var scope = new ContinuationScope("generator");
        continuation = new Continuation(scope, () -> {
          consumer.accept(v -> {
            value = v;
            Continuation.yield(scope);
          });
        });
        continuation.run();
      }
      
      public boolean hasNext() {
        return !continuation.isDone();
      }
      public T next() {
        if (!hasNext()) { throw new NoSuchElementException(); }
        var value = this.value;
        continuation.run();
        return value;
      }
    };
  }
  
  public static void main(String[] args) {
    class Triple {
      private final int x, y, z;  Triple(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
      public @Override String toString() { return "(" + x + ", " + y + ", " + z + ')'; }
    }
    
    Iterable<Triple> iterable = () -> iterator(yielder -> {
      for(var z = 1; z <= 100; z++) {
        for(var x = 1; x <= z; x++) {
          for(var y = x; y <= z; y++) {
            if (x*x + y * y == z * z) {
              yielder.accept(new Triple(x, y, z));
            }
          } 
        } 
      }
    });
    
    for(var triple: iterable) {
      System.out.println(triple);
    }
  }
}
