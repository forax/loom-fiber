package fr.umlv.loom.scheduler;

import java.util.ArrayDeque;

public class FifoScheduler implements Scheduler {
  private final ArrayDeque<Continuation> schedulable = new ArrayDeque<>();
  
  @Override
  public void register(Continuation continuation) {
    schedulable.add(continuation);
    if (!Scheduler.hasCurrentContinuation()) {
      loop();
    }
  }
  
  private void loop() {
    while(!schedulable.isEmpty()) {
      var continuation = schedulable.poll();
      continuation.run();
    }
  }
}
