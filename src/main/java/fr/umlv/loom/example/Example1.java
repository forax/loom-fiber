package fr.umlv.loom.example;

import java.util.concurrent.StructuredTaskScope;

public class Example1 {
  // async call
  public static void main(String[] args) throws InterruptedException {
    try(var scope = StructuredTaskScope.open()) {
      var future = scope.fork(() -> "hello");
      scope.join();
      System.out.println(future.resultNow());
    }
  }
}
