package fr.umlv.loom.continuation;

import java.util.ArrayDeque;

public class FifoScheduler implements Scheduler {
  private final ArrayDeque<Continuation> schedulable = new ArrayDeque<>();
  
  @Override
  public void register(Continuation continuation) {
    schedulable.add(continuation);
  }
  
  @Override
  public void loop() {
    while(!schedulable.isEmpty()) {
      var continuation = schedulable.poll();
      continuation.run();
    }
  }
}
