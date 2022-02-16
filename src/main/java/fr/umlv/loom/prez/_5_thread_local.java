package fr.umlv.loom.prez;

public class _5_thread_local {
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
