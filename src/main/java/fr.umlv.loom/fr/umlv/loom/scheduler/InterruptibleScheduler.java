package fr.umlv.loom.scheduler;

public class InterruptibleScheduler {
  public static void main(String[] args) {
    var scope = new ContinuationScope("scope");
    var continuation = new Continuation(scope, () -> {
      for(;;) {
        // do nothing
      }
    });
    
    var current = Thread.currentThread();
    
    var thread = new Thread(() -> {
      try {
        Thread.sleep(1_000);
      } catch (InterruptedException e) {
        throw new AssertionError(e);
      }
      System.out.println("preempt");
      var status = continuation.tryPreempt(current);
      System.out.println("preempt status " + status);
    });
    thread.start();
    
    continuation.run();
    
    System.out.println("it works !");
  }
}
