package fr.umlv.loom.example;

import jdk.incubator.concurrent.ScopedValue;

// $JAVA_HOME/bin/java --enable-preview --add-modules jdk.incubator.concurrent -cp target/loom-1.0-SNAPSHOT.jar  fr.umlv.loom.example._6_scoped_value
public class _6_scoped_value {
  private static final ScopedValue<String> USER = ScopedValue.newInstance();

  private static void sayHello() {
    System.out.println("Hello " + USER.get());
  }

  public static void main(String[] args) throws InterruptedException {
    var vthread = Thread.ofVirtual()
        .allowSetThreadLocals(false)
        .start(() -> {
          ScopedValue.where(USER, "Bob", () -> {
            sayHello();
          });
        });

    vthread.join();
  }
}
