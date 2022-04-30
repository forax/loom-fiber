package fr.umlv.loom.example;

import jdk.incubator.concurrent.ExtentLocal;

// $JAVA_HOME/bin/java --enable-preview --add-modules jdk.incubator.concurrent -cp target/loom-1.0-SNAPSHOT.jar  fr.umlv.loom.example._6_extent_local
public class _6_extent_local {
  private static final ExtentLocal<String> USER = ExtentLocal.newInstance();

  private static void sayHello() {
    System.out.println("Hello " + USER.get());
  }

  public static void main(String[] args) throws InterruptedException {
    var vthread = Thread.ofVirtual()
        .allowSetThreadLocals(false)
        .start(() -> {
          ExtentLocal.where(USER, "Bob", () -> {
            sayHello();
          });
        });

    vthread.join();
  }
}
