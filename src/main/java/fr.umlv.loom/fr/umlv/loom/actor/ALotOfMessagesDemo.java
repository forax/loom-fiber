package fr.umlv.loom.actor;

import static fr.umlv.loom.actor.Actor.current;
import static fr.umlv.loom.actor.Actor.exit;
import static fr.umlv.loom.actor.Actor.receive;

import java.util.Random;

public class ALotOfMessagesDemo {
  private static Actor forward(Actor next) {
    var actor = new Actor(new Runnable() {
      private int counter;
      
      @Override
      public void run() {
        while (true) {
          receive(message -> {
            switch((String)message) {
              case "inc": {
                if (next != null) {
                  next.send(message);
                  break;
                }
                current().orElseThrow().send("counter");
                break;
              }
              case "counter": counter++; break;
              case "end": {
                if (next != null) {
                  next.send(message);
                } else {
                  System.out.println(counter);
                }
                exit();
                break;
              }
              default: throw new IllegalStateException();
            }
          });
        }
      }
    });
    actor.start();
    return actor;
  }
  
  public static void main(String[] args) {
    var actors = new Actor[1_000];
    Actor next = null;
    for(var i = actors.length; --i >= 0; ) {
      var actor = forward(next);
      actors[i] = actor;
      next = actor;
    }

    new Random(0).ints(actors.length, 0, actors.length).forEach(i -> actors[i].send("inc"));
    
    actors[0].send("end");
  }
}
