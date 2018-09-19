package fr.umlv.loom.example;

public class Example1 {
  public static void main(String[] args) {
    var scope = new ContinuationScope("example1");
    var continuation = new Continuation(scope, () ->  {
      System.out.println("1");
      Continuation.yield(scope);
      System.out.println("2");
      Continuation.yield(scope);
      System.out.println("3");
    });
    
    System.out.println("A");
    continuation.run();
    System.out.println("B");
    continuation.run();
    System.out.println("C");
    continuation.run();
    System.out.println("D");
    //continuation.run();  //ISE: Continuation terminated
  }
}
