package fr.umlv.loom.example;

// $JAVA_HOME/bin/java --enable-preview -cp target/loom-1.0-SNAPSHOT.jar  fr.umlv.loom.prez._2_thread_builder
public interface _2_thread_builder {
  static void main(String[] args) throws InterruptedException {
    // platform thread
    var pthread = Thread.ofPlatform()
        .name("platform-", 0)
        .start(() -> {
          System.out.println("platform " + Thread.currentThread());
        });
    pthread.join();

    // virtual thread
    var vthread = Thread.ofVirtual()
        .name("virtual-", 0)
        .start(() -> {
          System.out.println("virtual " + Thread.currentThread());
        });
    vthread.join();
  }
}
