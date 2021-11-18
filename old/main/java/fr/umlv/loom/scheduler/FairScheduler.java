package fr.umlv.loom.continuation;

import java.util.IdentityHashMap;
import java.util.TreeMap;

public class FairScheduler implements Scheduler {
  private final TreeMap<Long, Continuation> schedulable = new TreeMap<>();
  private final IdentityHashMap<Continuation, Long> pendingMap = new IdentityHashMap<>();
  private long startTime;
  
  private void put(long executionTime, Continuation continuation) {
    var oldContinuation = schedulable.put(executionTime, continuation);
    if (oldContinuation == null) {
      return;
    }
    long time = executionTime;
    while (schedulable.containsKey(time)) {  // avoid collision
      time++;
    }
    schedulable.put(time, oldContinuation);
  }
  
  @Override
  public void register(Continuation continuation) {
    long executionTime = pendingMap.getOrDefault(continuation, 0L);
    if (executionTime >= 0) {
      put(executionTime, continuation);
      pendingMap.remove(continuation);
    }
  }
  
  @Override
  public void yield() {
    long executionTime = System.nanoTime() - startTime;
    var continuation = Scheduler.currentContinuation();
    if (pendingMap.get(continuation) == -1L) {
      put(executionTime, continuation);
      pendingMap.remove(continuation);
    }
    Scheduler.super.yield();
  }
  
  @Override
  public void loop() {
    while(!schedulable.isEmpty()) {
      var continuation = schedulable.pollFirstEntry().getValue();
      startTime = System.nanoTime();
      pendingMap.put(continuation, -1L);
      continuation.run();
    }
  }
}
