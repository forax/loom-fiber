package fr.umlv.loom.actor;

import static java.util.stream.IntStream.range;

public class ChainMain {
  interface Ping {
    void ping(int value);
  }

  public static void main(String[] args) throws InterruptedException {
    var actors = range(0, 100_000)
        .mapToObj(__ -> Actor.of(Ping.class))
        .toList();
    range(0, actors.size())
        .forEach(i -> {
          var actor = actors.get(i);
          var next = (i == actors.size() - 1)? null: actors.get(i + 1);
          actor.behavior(context -> value -> {
            if (next == null) {
              System.out.println("value " + value);
            } else {
              context.postTo(next, $ -> $.ping(value + 1));
            }
            context.shutdown();
          });
        });

    Actor.run(actors, context -> {
      context.postTo(actors.get(0), $ -> $.ping(1));
    });
  }
}
