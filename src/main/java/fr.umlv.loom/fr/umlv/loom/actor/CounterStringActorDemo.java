package fr.umlv.loom.actor;

import static fr.umlv.loom.actor.Actor.receive;
import static fr.umlv.loom.actor.Actor.exit;
import static java.util.stream.IntStream.range;

public class CounterStringActorDemo {
  public static void main(String[] args) {
    var actor = new Actor(new Runnable() {
      int count;
      
      @Override
      public void run() {
        while (true) {
          receive(message -> {
            switch((String)message) {
            case "increment":
              count++;
              break;
            case "value":
              System.out.println("Value is " + count);
              exit();
              break;
            }
          });
        }  
      }
    });
    actor.start();

    range(0, 100_000).forEach(__ -> actor.send("increment"));

    actor.send("value");
    // Output: Value is 100000
  }
}
