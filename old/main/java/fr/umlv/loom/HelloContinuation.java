package fr.umlv.loom;

public class HelloContinuation {
  public static void main(String[] args) {
    var scope = new ContinuationScope("hello");
    var cont = new Continuation(scope, () ->  {
      System.out.println("hello");
      
      Continuation.yield(scope);
      System.out.println("hello 2");
      
    });
    
    cont.run();
    System.out.println("i'm back");
    cont.run();
  }
}
