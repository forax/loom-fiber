package fr.umlv.loom.example;

public class Example4 {
  public static void main(String[] args) {
    var scope = new ContinuationScope("example4");
    var continuation = new Continuation(scope, () ->  {
      for(int i = 0; i < 2; i++) {
        System.out.println(i);
        Continuation.yield(scope);
      }
    });
    
    while(!continuation.isDone()) {
      System.out.println("run");
      continuation.run();
    }
  }
}
