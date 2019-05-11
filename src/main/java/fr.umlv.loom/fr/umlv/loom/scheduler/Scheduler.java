package fr.umlv.loom.scheduler;

public interface Scheduler {
  void register(Continuation continuation);
  void loop();
  
  default void schedule(Runnable runnable) {
    var continuation = new Continuation(SchedulerImpl.SCOPE, runnable);
    register(continuation);
  }
  
  default void yield() {
    currentContinuation(); // verify there is a current continuation
    Continuation.yield(SchedulerImpl.SCOPE);
  }
  
  default void pause() {
    register(currentContinuation());
    Continuation.yield(SchedulerImpl.SCOPE);
  }
  
  static Continuation currentContinuation() {
    var continuation = Continuation.getCurrentContinuation(SchedulerImpl.SCOPE);
    if (continuation == null) {
      throw new IllegalStateException("no current continuation");
    }
    return continuation;
  }
  static boolean hasCurrentContinuation() {
    return Continuation.getCurrentContinuation(SchedulerImpl.SCOPE) != null;
  }
}