package fr.umlv.loom.example;

// $JAVA_HOME/bin/java -cp target/classes  fr.umlv.loom.example._6_thread_local
public class _6_thread_local {
  private static final ThreadLocal<String> USER = new ThreadLocal<>();

  private static void sayHello() {
    System.out.println("Hello " + USER.get());
  }

  public static void main(String[] args) throws InterruptedException {
    var vthread = Thread.ofVirtual().start(() -> {
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
