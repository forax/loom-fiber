package fr.umlv.loom;

import static java.util.Objects.requireNonNull;

import java.util.function.Supplier;

public class Context<V> {
  private final ContinuationScope scope = new ContinuationScope("context");
  private final Supplier<? extends V> initialSupplier;
  
  static class ContextContinuation<V> extends Continuation {
    private V value;
    
    public ContextContinuation(ContinuationScope scope, Runnable runnable) {
      super(scope, runnable);
    }
  }
  
  public Context(Supplier<? extends V> initialSupplier) {
    this.initialSupplier = initialSupplier;
  }
  
  public V getValue() {
    @SuppressWarnings("unchecked")
    var continuation = (ContextContinuation<V>)Continuation.getCurrentContinuation(scope);
    if (continuation == null) {
      throw new IllegalStateException();
    }
    var value = continuation.value;
    if (value == null) {
      return continuation.value = requireNonNull(initialSupplier.get());
    }
    return value;
  }
  public void setValue(V value) {
    requireNonNull(value);
    @SuppressWarnings("unchecked")
    var continuation = (ContextContinuation<V>)Continuation.getCurrentContinuation(scope);
    if (continuation == null) {
      throw new IllegalStateException();
    }
    continuation.value = value;
  }
  
  public void enter(Runnable runnable) {
    new ContextContinuation<>(scope, runnable).run();
  }
}
