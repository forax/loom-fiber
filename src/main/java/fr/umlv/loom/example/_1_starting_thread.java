package fr.umlv.loom.example;

// $JAVA_HOME/bin/java --enable-preview -cp target/loom-1.0-SNAPSHOT.jar  fr.umlv.loom.example._1_starting_thread
public interface _1_starting_thread {
  static void main(String[] args) throws InterruptedException {
    // platform threads
    var pthread = new Thread(() -> {
      System.out.println("platform " + Thread.currentThread());
    });
    pthread.start();
    pthread.join();

    // virtual threads
    var vthread = Thread.startVirtualThread(() -> {
      System.out.println("virtual " + Thread.currentThread());
    });
    vthread.join();
  }
}
