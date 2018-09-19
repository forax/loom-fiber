package fr.umlv.loom.example;

public class Example2 {
  public static void main(String[] args) {
    var scope = new ContinuationScope("example2");
    var continuation = new Continuation(scope, () ->  {
      var current = Continuation.getCurrentContinuation(scope);
      System.out.println("current " + current);
    });
    
    System.out.println("continuation " + continuation);
    continuation.run();
  }
}
