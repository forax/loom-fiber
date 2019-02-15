package fr.umlv.loom.scheduler;

public interface Scheduler {
  void register(Continuation continuation);
  
  default void execute(Runnable runnable) {
    var continuation = new Continuation(SchedulerImpl.SCOPE, runnable);
    register(continuation);
  }
  
  default void pause() {
    register(currentContinuation());
    Continuation.yield(SchedulerImpl.SCOPE);
  }
  
  
  public static Continuation currentContinuation() {
    var continuation = Continuation.getCurrentContinuation(SchedulerImpl.SCOPE);
    if (continuation == null) {
      throw new IllegalStateException("no current continuation");
    }
    return continuation;
  }
  public static boolean hasCurrentContinuation() {
    return Continuation.getCurrentContinuation(SchedulerImpl.SCOPE) != null;
  }
  
  public static void yield() {
    Continuation.yield(SchedulerImpl.SCOPE);
  }
}