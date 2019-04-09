package fr.umlv.loom.actor;

import static fr.umlv.loom.actor.Actor.exit;
import static fr.umlv.loom.actor.Actor.receive;
import static fr.umlv.loom.actor.Case.Case;
import static java.util.stream.IntStream.range;

public class CounterActorDemo {
  static class Inc {
    final int amount;

    public Inc(int amount) {
      this.amount = amount;
    }
  }
  static class Value { /* empty */ }

  public static void main(String[] args) {
    var actor = new Actor(new Runnable() {
      int counter;

      @Override
      public void run() {
        while (true) {
          receive(
            Case(Inc.class, inc ->
                counter += inc.amount
            ),
            Case(Value.class, value -> {
                System.out.println("Value is " + counter);
                exit();
            })
          );
        }
      }
    });
    actor.start();

    range(0, 100_000).forEach(__ -> actor.send(new Inc(1)));

    actor.send(new Value());
    // Output: Value is 100000
  }
}
