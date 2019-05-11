package fr.umlv.loom.realtime;

import java.util.ArrayList;
import java.util.PriorityQueue;

public class Realtime {
  private Realtime() {
    // empty
  }
  
  public interface LightweightThread {
    int priority();
    
    void start();
  }
  
  public interface LightweightLock {
    void lock();
    void unlock();
  }
  
  private static final Scheduler SCHEDULER = new Scheduler();
  private static final ContinuationScope SCOPE = new ContinuationScope("RT");
  
  public static LightweightThread createLightweightThread(int priority, Runnable runnable) {
    return new RTContinuation(priority, runnable, SCHEDULER);
  }
  
  public static LightweightLock createLightweightLock() {
    return new RTLock();
  }
  
  static class RTLock implements LightweightLock {
    private boolean locked;
    private final ArrayList<RTContinuation> waits = new ArrayList<>();
    
    @Override
    public void lock() {
      for(;;) {
        if (!locked) {
          locked = true;
        } else {
          var currentContinuation = RTContinuation.currentContinuation();
          waits.add(currentContinuation);
          Continuation.yield(SCOPE);
        }
      }
    }
    
    @Override
    public void unlock() {
      locked = false;
      waits.forEach(SCHEDULER.queue::add);
      waits.clear();
      SCHEDULER.yield(RTContinuation.currentContinuation());
    }
  }
  
  static class RTContinuation extends Continuation implements LightweightThread {
    private final int priority;
    private final Scheduler scheduler;
    
    private RTContinuation(int priority, Runnable runnable, Scheduler scheduler) {
      super(SCOPE, runnable);
      this.priority = priority;
      this.scheduler = scheduler;
    }
    
    @Override
    public int priority() {
      return priority;
    }
    
    @Override
    public void start() {
      scheduler.schedule(this);
    }
    
    private static RTContinuation currentContinuation() {
      return (RTContinuation)Continuation.getCurrentContinuation(SCOPE);
    }
  }
  
  static class Scheduler {
    private final PriorityQueue<RTContinuation> queue = new PriorityQueue<>((ct1, ct2) -> ct1.priority - ct2.priority);

    private void schedule(RTContinuation continuation) {
      queue.add(continuation);
      if (queue.size() == 1) {
        loop();
      }
    }
    
    private void yield(RTContinuation continuation) {
      queue.add(continuation);
      Continuation.yield(SCOPE);
    }
    
    private void loop() {
      for(;;) {
        RTContinuation continuation = queue.poll();
        if (continuation == null) {
          return;
        }
        continuation.run();
      }
    }
  }
}
