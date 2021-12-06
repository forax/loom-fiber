package fr.umlv.loom.actor;

import fr.umlv.loom.actor.Actor.Context;
import fr.umlv.loom.actor.Actor.ShutdownSignal;

import java.util.List;

public class HelloSpawnMain {
  public static void main(String[] args) throws InterruptedException {
    record Hello(Context context) {
      public void say(String message) {
        System.out.println("Hello " + message);
      }
    }
    record Callback(Context context) {
      public void callHello(Actor<Hello> hello) {
        context.postTo(hello, $ -> $.say("actor using loom"));
      }
    }
    record Manager(Context context) {
      public void createHello(Actor<Callback> callback) {
        var hello = Actor.of(Hello.class)
            .behavior(Hello::new);
        context.spawn(hello);
        context.postTo(callback, $ -> $.callHello(hello));
      }

      public void end() {
        context.shutdown();
      }
    }

    var callback = Actor.of(Callback.class)
        .behavior(Callback::new);
    var manager = Actor.of(Manager.class)
        .behavior(Manager::new)
        .onSignal((signal, context) -> context.signal(callback, ShutdownSignal.INSTANCE));

    Actor.run(List.of(manager, callback), context -> {
      context.postTo(manager, $ -> $.createHello(callback));
      context.postTo(manager, $ -> $.end());
    });
  }
}
