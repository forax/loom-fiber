package fr.umlv.loom;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;

public class ProducerConsumer {
  private static void produce(BlockingQueue<String> queue) {
    try {
      for(;;) {
        queue.put("hello ");
        System.out.println("produce " + Thread.currentThread());

        Thread.sleep(200);
      }
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }
  }

  private static void consume(BlockingQueue<String> queue) {
    try {
      for(;;) {
        var message = queue.take();
        System.out.println("consume " + Thread.currentThread() + " received " + message);

        Thread.sleep(200);
      }
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }
  }

  public static void main(String[] args) {
    var queue = new ArrayBlockingQueue<String>(8192);
    
//    try (var scheduler = Executors.newFixedThreadPool(1)) {
//      var factory =
//Thread.builder().virtual(scheduler).factory();
//      try (var executor =
//Executors.newUnboundedExecutor(factory)) {
//          executor.submit(() -> produce(queue));
//          executor.submit(() -> consume(queue));
//      }
//  }
    
    var scheduler = Executors.newFixedThreadPool(1);
    Thread.builder().name("producer").virtual(scheduler).task(() -> produce(queue)).build().start();
    Thread.builder().name("consumer").virtual(scheduler).task(() -> consume(queue)).build().start();
  }
}
