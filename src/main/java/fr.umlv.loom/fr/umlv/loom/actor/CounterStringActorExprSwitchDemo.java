package fr.umlv.loom.actor;

import static fr.umlv.loom.actor.Actor.exit;
import static fr.umlv.loom.actor.Actor.receive;
import static java.util.stream.IntStream.range;

import java.util.function.Consumer;

public class CounterStringActorExprSwitchDemo {
  public static void main(String[] args) {
    var actor = new Actor(new Runnable() {
      int counter;

      @Override
      public void run() {
        while (true) {
          receive(message -> {
            switch((String)message) {
            case "increment" ->
              counter++;
            case "value" -> {
              System.out.println("Value is " + counter);
              exit();
            }
          }});
        }
      }  
    });
    actor.start();

    range(0, 100_000).forEach(__ -> actor.send("increment"));

    actor.send("value");
    // Output: Value is 100000
  }
}
