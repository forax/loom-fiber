package fr.umlv.loom.actor;

import fr.umlv.loom.actor.Actor.ShutdownSignal;

import java.util.List;

public class HelloSpawnMain {
  public static void main(String[] args) throws InterruptedException {
    interface Hello {
      void say(String message);
    }
    interface Callback {
      void thisIsHello(Actor<Hello> hello);
    }
    interface Manager {
      void createHello(Actor<Callback> callback);
      void end();
    }

    var callback = Actor.of(Callback.class)
        .behavior(context -> new Callback() {
          @Override
          public void thisIsHello(Actor<Hello> hello) {
            context.postTo(hello, $ -> $.say("actor using loom"));
          }
        });
    var manager = Actor.of(Manager.class)
        .behavior(context -> new Manager() {
          @Override
          public void createHello(Actor<Callback> callback) {
            var hello = Actor.of(Hello.class)
                .behavior(context -> new Hello() {
                  @Override
                  public void say(String message) {
                System.out.println("Hello " + message);
              }
                });
            context.spawn(hello);
            context.postTo(callback, $ -> $.thisIsHello(hello));
          }

          @Override
          public void end() {
            context.shutdown();
          }
        })
        .onSignal((signal, context) -> context.signal(callback, ShutdownSignal.INSTANCE));

    Actor.run(List.of(manager, callback), context -> {
      context.postTo(manager, $ -> $.createHello(callback));
      context.postTo(manager, $ -> $.end());
    });
  }
}
