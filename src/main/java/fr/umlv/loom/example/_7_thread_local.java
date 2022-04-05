package fr.umlv.loom.example;

// $JAVA_HOME/bin/java --enable-preview -cp target/loom-1.0-SNAPSHOT.jar  fr.umlv.loom.example._7_thread_local
public class _7_thread_local {
  private static final ThreadLocal<String> USER = new ThreadLocal<>();

  private static void sayHello() {
    System.out.println("Hello " + USER.get());
  }

  public static void main(String[] args) throws InterruptedException {
    var vthread = Thread.ofVirtual()
        //.allowSetThreadLocals(true)
        .start(() -> {
      USER.set("Bob");
      try {
        sayHello();
      } finally {
        USER.remove();
      }
    });

    vthread.join();
  }
}
