package fr.umlv.loom.actor;

import fr.umlv.loom.actor.Actor.PanicSignal;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RestartMain {
  private static void sleep(long time) {
    try {
      Thread.sleep(time);
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }
  }

  public static void main(String[] args) throws InterruptedException {
    interface Simple {
      void message(String text) throws Exception;
    }

    var retries = new AtomicInteger(1);

    var simple = Actor.of(Simple.class)
        .behavior(context -> new Simple() {
          int value;

          @Override
          public void message(String text) throws Exception {
            System.out.println(text);
            throw new Exception("database error " + value++);  // always 0, the behavior is reset
          }
        })
        .onSignal((signal, context) -> {
          if (signal instanceof PanicSignal panicSignal) {
            //can use signal.exception() here !

            if (retries.getAndIncrement() < 3) {
              context.restart();
            }
          }
        });

    Actor.run(List.of(simple), context -> {
      context.postTo(simple, $ -> $.message("1"));
      sleep(1_000);
      context.postTo(simple, $ -> $.message("2"));
      sleep(1_000);
      context.postTo(simple, $ -> $.message("3"));
      sleep(1_000);
      context.postTo(simple, $ -> $.message("4"));
    });
  }
}
