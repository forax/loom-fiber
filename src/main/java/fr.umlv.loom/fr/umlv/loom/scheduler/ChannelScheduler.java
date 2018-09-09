package fr.umlv.loom.scheduler;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.stream.IntStream;

public class ChannelScheduler {
  final ContinuationScope scope;
  final ArrayDeque<Continuation> schedulable = new ArrayDeque<>();
  
  public ChannelScheduler() {
    scope = new ContinuationScope("scheduler-" + Integer.toHexString(System.identityHashCode(this)));
  }
  
  Continuation currentContinuation() {
    var currentContinuation = Continuation.getCurrentContinuation(scope);
    if (currentContinuation == null) {
      throw new IllegalStateException("no current continuation");
    }
    return currentContinuation;
  }
  
  public void execute(Runnable runnable) {
    var continuation = new Continuation(scope, runnable);
    schedulable.add(continuation);
    
    var currentContinuation = Continuation.getCurrentContinuation(scope);
    if (currentContinuation == null) {
      loop();
    }
  }
  
  void loop() {
    while(!schedulable.isEmpty()) {
      Continuation continuation = schedulable.poll();
      continuation.run();
    }
  }
  
  static class Channel<V> {
    private final ChannelScheduler scheduler;
    private final ArrayDeque<Continuation> producerQueue = new ArrayDeque<>();
    private final ArrayDeque<Continuation> consumerQueue = new ArrayDeque<>();
    private V value;
    
    public Channel(ChannelScheduler scheduler) {
      this.scheduler = Objects.requireNonNull(scheduler);
    }

    public V take() {
      if (!producerQueue.isEmpty()) {
        var producer = producerQueue.poll();
        scheduler.schedulable.addFirst(producer);
      }
      consumerQueue.add(scheduler.currentContinuation());
      Continuation.yield(scheduler.scope);
      var value = this.value;
      this.value = null;
      return value;
    }
    
    public void put(V value) {
      Objects.requireNonNull(value);
      if(consumerQueue.isEmpty()) {
        producerQueue.add(scheduler.currentContinuation());
        Continuation.yield(scheduler.scope);
      }
      this.value = value;
      var consumer = consumerQueue.poll();
      scheduler.schedulable.addFirst(consumer);
      Continuation.yield(scheduler.scope);
    }
  }
  
  public static void main(String[] args) {
    var scheduler = new ChannelScheduler();
    var channel = new Channel<Integer>(scheduler);
    IntStream.range(0, 2).forEach(id -> {
      scheduler.execute(() -> {
        System.out.println("consumer " + id + ": take " + channel.take());
      });
    });
    IntStream.range(0, 2).forEach(id -> {
      scheduler.execute(() -> {
        System.out.println("producer " + id + ": put " + id);
        channel.put(id);
      });
    });
  }
}
