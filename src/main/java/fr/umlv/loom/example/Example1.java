package fr.umlv.loom.example;

import java.util.concurrent.StructuredExecutor;

public class Example1 {
  // async call
  public static void main(String[] args) throws InterruptedException {
    try(var executor = StructuredExecutor.open()) {
      executor.execute(() -> System.out.println("hello"));
      executor.join();
    }
  }
}
