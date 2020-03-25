package fr.umlv.loom.actor;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class Actor {
  private static final ContinuationScope SCOPE = new ContinuationScope("actor");
  private static final Scheduler SCHEDULER = new Scheduler();
  
  private static class Message {
    private final Object content;
    private final Actor sender;
    
    private Message(Object content, Actor sender) {
      this.content = content;
      this.sender = sender;
    }
  }
  
  private static class Scheduler {
    private final LinkedHashSet<Actor> actors = new LinkedHashSet<>();
    private boolean inLoop;
    
    private void register(Actor actor) {
      actors.add(actor);
      if (!inLoop && actors.size() == 1) {
        loop();
      }
    }
    
    private void loop() {
      inLoop = true;
      try {
        while(!actors.isEmpty()) {
          var iterator = actors.iterator();
          var actor = iterator.next(); 
          iterator.remove();
          actor.continuation.run();
        }
      } finally {
        inLoop = false;
      }
    }
  }
  
  private class ActorContinuation extends Continuation {
    private ActorContinuation(Runnable runnable) {
      super(SCOPE, runnable);
    }
    
    Actor actor() {
      return Actor.this;
    }
  }
  
  
  private final ArrayDeque<Message> mailbox = new ArrayDeque<>();
  private final ActorContinuation continuation;
  
  public Actor(Runnable runnable) {
    continuation = new ActorContinuation(() -> {
      try {
        runnable.run();
      } catch(@SuppressWarnings("unused") ExitError exit) {
        return;
      } 
    });
  }
  
  public static void receive(Case... cases) {
    receive((content, sender) -> Case.call(content, sender, cases));
  }
  
  public static void receive(Consumer<Object> consumer) {
    receive((content, sender) -> consumer.accept(content));
  }
  
  public static void receive(BiConsumer<Object, ? super Actor> consumer) {
    var actor = current().orElseThrow(() -> new IllegalStateException("no current actor available"));
    while(actor.mailbox.isEmpty()) {
      Continuation.yield(SCOPE);
    }
    Message message;
    while((message = actor.mailbox.poll()) != null) {
      consumer.accept(message.content, message.sender);
    }
  }
  
  public static Optional<Actor> current() {
    var currentContinuation = (ActorContinuation)Continuation.getCurrentContinuation(SCOPE);
    return Optional.ofNullable(currentContinuation).map(ActorContinuation::actor);
  }
  
  public void send(Object message) {
    mailbox.add(new Message(message, current().orElse(null)));
    SCHEDULER.register(this);
  }
  
  public void start() {
    SCHEDULER.register(this);
  }
  
  @SuppressWarnings("serial")
  private static class ExitError extends Error {
    private static final ExitError INSTANCE = new ExitError();
    private ExitError() {
      super(null, null, false, false);
    }
  }
  
  public static <T> T exit() {
    current().orElseThrow(() -> new IllegalStateException("no current actor available"));
    throw ExitError.INSTANCE;
  }
}