package fr.umlv.loom.continuation;

public class ContinuationMain {
  public static void main(String[] args) {
    var continuation = new Continuation(() -> {
      System.out.println("C1");
      Continuation.yield();
      System.out.println("C2");
      Continuation.yield();
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
