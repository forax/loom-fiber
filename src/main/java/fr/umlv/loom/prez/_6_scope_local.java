package fr.umlv.loom.prez;

import jdk.incubator.concurrent.ScopeLocal;

public class _6_scope_local {
  private static final ScopeLocal<String> USER = ScopeLocal.newInstance();

  private static void sayHello() {
    System.out.println("Hello " + USER.get());
  }

  public static void main(String[] args) throws InterruptedException {
    var vthread = Thread.ofVirtual()
        .allowSetThreadLocals(true)
        .start(() -> {

      ScopeLocal.where(USER, "Bob", () -> {
        sayHello();
      });
    });

    vthread.join();
  }
}
