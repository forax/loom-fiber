package fr.umlv.loom.scheduler;

import java.util.Objects;

public class ExchangerExample {
  public static class Exchanger<V> {
    private final Scheduler scheduler;
    private V value;
    private Continuation continuation;
    
    public Exchanger(Scheduler scheduler) {
      this.scheduler = Objects.requireNonNull(scheduler);
    }

    public V exchange(V value) {
      Objects.requireNonNull(value);
      var continuation = Scheduler.currentContinuation();
      if (this.value == null) {
        this.value = value;
        this.continuation = continuation;
        Scheduler.yield();
        var result = this.value;
        this.value = null;
        return result;
      }
      var result = this.value;
      this.value = value;
      scheduler.register(this.continuation);
      this.continuation = null;
      return result;
    }
  }
  
  public static void main(String[] args) {
    var scheduler = new FifoScheduler();
    var exchanger = new Exchanger<String>(scheduler);
    scheduler.execute(() -> {
      System.out.println("cont1: " + exchanger.exchange("hello"));
    });
    scheduler.execute(() -> {
      System.out.println("cont2: " + exchanger.exchange("hi"));
    });
  }
}
