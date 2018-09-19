package fr.umlv.loom.example;

public class Example3 {
  public static void main(String[] args) {
    var scope = new ContinuationScope("example3");
    var data = new Object() {
      boolean end;
    };
    var continuation = new Continuation(scope, () ->  {
      for(int i = 0; i < 2; i++) {
        System.out.println(i);
        Continuation.yield(scope);
      }
      data.end = true;
    });
    
    while(!data.end) {
      System.out.println("run");
      continuation.run();
    }
  }
}
