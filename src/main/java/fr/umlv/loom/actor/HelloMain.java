package fr.umlv.loom.actor;

import java.util.List;

public class HelloMain {
  public static void main(String[] args) throws InterruptedException {
    interface Hello {
      void say(String message);
      void end();
    }

    var hello = Actor.of(Hello.class);
    hello.behavior(context -> new Hello() {
      @Override
      public void say(String message) {
        System.out.println("Hello " + message);
      }
      @Override
      public void end() {
        context.shutdown();
      }
    });

    Actor.run(List.of(hello), context -> {
      context.postTo(hello, $ -> $.say("actors using loom"));
      context.postTo(hello, $ -> $.end());
    });
  }
}
