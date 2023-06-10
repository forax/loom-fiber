package fr.umlv.loom.example;

import jdk.internal.vm.Continuation;
import jdk.internal.vm.ContinuationScope;

// $JAVA_HOME/bin/java --add-exports java.base/jdk.internal.vm=ALL-UNNAMED -cp target/classes  fr.umlv.loom.example._5_continuation
public interface _5_continuation {
  static void main(String[] args)  {
    var scope = new ContinuationScope("hello");
    var continuation = new Continuation(scope, () -> {
      System.out.println("C1");
      Continuation.yield(scope);
      System.out.println("C2");
      Continuation.yield(scope);
      System.out.println("C3");
    });

    System.out.println("start");
    continuation.run();
    System.out.println("came back");
    continuation.run();
    System.out.println("back again");
    continuation.run();
    System.out.println("back again again");
  }
}
