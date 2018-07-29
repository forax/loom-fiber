package fr.umlv.loom;

import java.util.Objects;
import java.util.function.BiFunction;

public class EventContinuation<P,R> {
  private static final ContinuationScope SCOPE = new ContinuationScope() { /*empty*/ }; 
  
  private final Continuation continuation;
  private P parameter;
  private R yieldValue;
  
  public interface Yielder<P,R> {
    public P yield(R yieldValue);
  }
  
  public EventContinuation(BiFunction<Yielder<? extends P, ? super R>, ? super P, ? extends R> consumer) {
    Objects.requireNonNull(consumer);
    continuation = new Continuation(SCOPE, () -> {
      this.yieldValue = consumer.apply(yieldValue -> {
        this.yieldValue = yieldValue;
        Continuation.yield(SCOPE);
        P parameter = this.parameter;
        this.parameter = null;
        return parameter;
      }, parameter);
    });
  }
  
  public R execute(P parameter) {
    this.parameter = parameter;
    continuation.run();
    R yieldValue = this.yieldValue;
    this.yieldValue = null;
    return yieldValue;
  }
}
