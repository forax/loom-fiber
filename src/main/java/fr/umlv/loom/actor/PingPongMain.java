package fr.umlv.loom.actor;

import fr.umlv.loom.actor.Actor.Context;

import java.util.List;

public class PingPongMain {
  record Ping(Context context, Actor<Pong> pongActor) {
    public void ping(int value) {
      //System.out.println("actor1 ping " + value);
      if (value >= 10_000) {
        context.postTo(pongActor, $ -> $.end(value));
        return;
      }
      context.postTo(pongActor, $ -> $.pong(value + 1));
    }

    public void end() {
      System.out.println(context.currentActor(Ping.class) + " end");
      context.shutdown();
    }
  }

  record Pong(Context context, Actor<Ping> pingActor) {
    public void pong(int value) {
      //System.out.println("actor2 pong " + value);
      context.postTo(pingActor, $ -> $.ping(value + 1));
    }

    public void end(int value) {
      System.out.println("value " + value);
      context.postTo(pingActor, Ping::end);
      System.out.println(context.currentActor(Pong.class) + " end");
      context.shutdown();
    }
  }

  public static void main(String[] args) throws InterruptedException {
    var pingActor = Actor.of(Ping.class);
    var pongActor = Actor.of(Pong.class);

    pingActor.behavior(context -> new Ping(context, pongActor));
    pongActor.behavior(context -> new Pong(context, pingActor));

    Actor.run(List.of(pingActor, pongActor), ctx -> {
      ctx.postTo(pingActor, $ -> $.ping(0));
    });
  }
}
