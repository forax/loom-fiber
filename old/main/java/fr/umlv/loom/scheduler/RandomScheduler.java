package fr.umlv.loom.continuation;

import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

public class RandomScheduler implements Scheduler {
  private final ArrayList<Continuation> schedulable = new ArrayList<>();
  
  @Override
  public void register(Continuation continuation) {
    schedulable.add(continuation);
  }
  
  @Override
  public void loop() {
    var random = ThreadLocalRandom.current();
    while(!schedulable.isEmpty()) {
      var continuation = schedulable.remove(random.nextInt(schedulable.size()));
      continuation.run();
    }
  }
}
