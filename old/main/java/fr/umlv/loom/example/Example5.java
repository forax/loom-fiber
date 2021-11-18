package fr.umlv.loom.example;

import java.util.ArrayDeque;
import java.util.stream.IntStream;

public class Example5 {
  public static void main(String[] args) {
    var scope = new ContinuationScope("example5");
    var schedulable = new ArrayDeque<Continuation>();
    
    IntStream.range(0, 2).forEach(id -> {
      var continuation = new Continuation(scope, () ->  {
        for(int i = 0; i < 2; i++) {
          System.out.println("id" + id + " " + i);
          
          schedulable.add(Continuation.getCurrentContinuation(scope));
          Continuation.yield(scope);
        }
      });
      schedulable.add(continuation);
    });
    
    while(!schedulable.isEmpty()) {
      schedulable.poll().run();
    }
  }
}
