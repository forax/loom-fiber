package fr.umlv.loom.scheduler;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.stream.IntStream;

public class ChannelExample {
  public static class SingleValueChannel<V> {
    private final Scheduler scheduler;
    private final ArrayDeque<Continuation> producerQueue = new ArrayDeque<>();
    private final ArrayDeque<Continuation> consumerQueue = new ArrayDeque<>();
    private V value;
    
    public SingleValueChannel(Scheduler scheduler) {
      this.scheduler = Objects.requireNonNull(scheduler);
    }

    public V take() {
      if (!producerQueue.isEmpty()) {
        var producer = producerQueue.poll();
        scheduler.register(producer);
      }
      consumerQueue.add(Scheduler.currentContinuation());
      Scheduler.yield();
      var value = this.value;
      this.value = null;
      return value;
    }
    
    public void put(V value) {
      Objects.requireNonNull(value);
      if(consumerQueue.isEmpty()) {
        producerQueue.add(Scheduler.currentContinuation());
        Scheduler.yield();
      }
      this.value = value;
      var consumer = consumerQueue.poll();
      scheduler.register(consumer);
      Scheduler.yield();
    }
  }
  
  public static void main(String[] args) {
    var scheduler = new FifoScheduler();
    var channel = new SingleValueChannel<Integer>(scheduler);
    IntStream.range(0, 4).forEach(id -> {
      scheduler.execute(() -> {
        System.out.println("consumer " + id + ": take " + channel.take());
      });
    });
    IntStream.range(0, 4).forEach(id -> {
      scheduler.execute(() -> {
        System.out.println("producer " + id + ": put " + id);
        channel.put(id);
      });
    });
  }
}
