package fr.umlv.loom.actor;

import java.util.List;

public class PingPongMain {
  interface Ping {
    void ping(int value);
    void end();
  }

  interface Pong {
    void pong(int value);
    void end(int value);
  }

  public static void main(String[] args) throws InterruptedException {
    var actor1 = Actor.of(Ping.class);
    var actor2 = Actor.of(Pong.class);

    actor1.behavior(context -> new Ping() {
      @Override
      public void ping(int value) {
        //System.out.println("actor1 ping " + value);
        if (value >= 10_000) {
          context.postTo(actor2, $ -> $.end(value));
          return;
        }
        context.postTo(actor2, $ -> $.pong(value + 1));
      }

      @Override
      public void end() {
        System.out.println(actor1 + " end");
        context.shutdown();
      }
    });

    actor2.behavior(context -> new Pong() {
      @Override
      public void pong(int value) {
        //System.out.println("actor2 pong " + value);
        context.postTo(actor1, $ -> $.ping(value + 1));
      }

      @Override
      public void end(int value) {
        System.out.println("value " + value);
        context.postTo(actor1, Ping::end);
        System.out.println(actor2 + " end");
        context.shutdown();
      }
    });

    Actor.run(List.of(actor1, actor2), ctx -> {
      ctx.postTo(actor1, $ -> $.ping(0));
    });
  }
}
