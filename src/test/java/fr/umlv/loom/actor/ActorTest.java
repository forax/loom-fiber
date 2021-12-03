package fr.umlv.loom.actor;

import fr.umlv.loom.actor.Actor.IllegalActorStateException;
import fr.umlv.loom.actor.Actor.PanicSignal;
import fr.umlv.loom.actor.Actor.ShutdownSignal;
import fr.umlv.loom.actor.Actor.State;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.*;

public class ActorTest {

  @Test
  public void stateCreated() {
    interface Empty { }
    assertAll(
        () -> assertEquals(State.CREATED, Actor.of(Empty.class).state()),
        () -> assertEquals(State.CREATED, Actor.of(Empty.class, "empty").state())
    );
  }

  @Test @Timeout(value = 500, unit = MILLISECONDS)
  public void stateRunning() throws InterruptedException {
    interface Simple {
      void message();
    }
    var actor = Actor.of(Simple.class);
    actor.behavior(context -> () -> {
      assertEquals(State.RUNNING, actor.state());
      context.shutdown();
    });
    Actor.run(List.of(actor), startContext -> {
      startContext.postTo(actor, Simple::message);
    });
  }

  @Test @Timeout(value = 500, unit = MILLISECONDS)
  public void stateShutdown() throws InterruptedException {
    interface Simple {
      void message();
    }
    var actor = Actor.of(Simple.class)
        .behavior(context -> context::shutdown);
    Actor.run(List.of(actor), startContext -> {
      startContext.postTo(actor, Simple::message);
    });
    assertEquals(State.SHUTDOWN, actor.state());
  }

  @Test
  public void ofName() {
    interface Empty { }
    assertAll(
        () -> assertEquals("hello", Actor.of(Empty.class, "hello").name()),
        () -> assertThrows(NullPointerException.class, () -> Actor.of(Empty.class, null))
    );
  }

  @Test
  public void ofBehaviorTypeNull() {
    assertAll(
        () -> assertThrows(NullPointerException.class, () -> Actor.of(null)),
        () -> assertThrows(NullPointerException.class, () -> Actor.of(null, "hello"))
    );
  }

  @Test
  public void behaviorNull() {
    var actor = Actor.of(Runnable.class);
    assertThrows(NullPointerException.class, () -> actor.behavior(null));
  }

  @Test
  public void noBehaviorDefined() {
    var actor = Actor.of(Runnable.class);
    assertThrows(IllegalActorStateException.class, () -> Actor.run(List.of(actor), startContext -> {}));
  }

  @Test
  public void behaviorWrongThread() {
    var actor = Actor.of(Runnable.class);
    new Thread(() -> {
      assertThrows(IllegalActorStateException.class, () -> actor.behavior(context -> () -> {}));
    }).start();
  }

  @Test
  public void behaviorSeveralMessages() throws InterruptedException {
    interface Dummy {
      void foo(String message);
      void bar(int value);
      void stop();
    }
    var actor = Actor.of(Dummy.class)
        .behavior(context -> new Dummy() {
          @Override
          public void foo(String message) {
            assertEquals("hello", message);
          }

          @Override
          public void bar(int value) {
            assertEquals(42, value);
          }

          @Override
          public void stop() {
            context.shutdown();
          }
        });
    Actor.run(List.of(actor), context -> {
      context.postTo(actor, $ -> $.foo("hello"));
      context.postTo(actor, $ -> $.bar(42));
      context.postTo(actor, Dummy::stop);
    });
  }

  @Test
  public void onSignalNull() {
    var actor = Actor.of(Runnable.class)
        .behavior(context -> () -> {});
    assertThrows(NullPointerException.class, () -> actor.onSignal(null));
  }

  @Test
  public void onSignalWrongThread() {
    var actor = Actor.of(Runnable.class)
        .behavior(context -> () -> {});
    new Thread(() -> {
      assertThrows(IllegalActorStateException.class, () -> actor.onSignal((signal, context) -> {}));
    }).start();
  }

  @Test
  public void onSignalExeception() throws InterruptedException {
    interface Transparent {
      void message(Exception exception) throws Exception;
    }
    var actor = Actor.of(Transparent.class)
        .behavior(context -> exception -> { throw exception; })
        .onSignal((signal, context) -> {
          assertTrue(signal instanceof PanicSignal);
          var panicSignal = (PanicSignal) signal;
          var exception = panicSignal.exception();
          assertAll(
              () -> assertEquals(Exception.class, exception.getClass()),
              () -> assertEquals("foo", exception.getMessage())
          );
        });
    Actor.run(List.of(actor), context -> {
      context.postTo(actor, $ -> $.message(new Exception("foo")));
    });
    assertEquals(State.SHUTDOWN, actor.state());
  }

  @Test
  public void currentActor() throws InterruptedException {
    var box = new Object() { Actor<Runnable> actor; };
    var actor = Actor.of(Runnable.class);
    actor.behavior(context -> () -> {
      box.actor = context.currentActor(Runnable.class);
      context.shutdown();
    });
    Actor.run(List.of(actor), context -> {
      context.postTo(actor, Runnable::run);
    });
    assertSame(actor, box.actor);
  }

  @Test
  public void currentActorWrongBehaviorClass() throws InterruptedException {
    var box = new Object() { Exception exception; };
    var actor = Actor.of(Runnable.class)
        .behavior(context -> () -> {
          context.currentActor(String.class);   // oops
        })
        .onSignal((signal, context) -> {
          box.exception = ((PanicSignal) signal).exception();
        });
    Actor.run(List.of(actor), context -> {
      context.postTo(actor, Runnable::run);
    });
    assertEquals(IllegalActorStateException.class, box.exception.getClass());
  }

  @Test
  public void escapeActorContext() throws InterruptedException {
    var box = new Object() { Actor.Context context; };
    var actor = Actor.of(Runnable.class);
    actor.behavior(context -> () -> {
      box.context = context;
      context.shutdown();
    });
    Actor.run(List.of(actor), context -> {
      context.postTo(actor, Runnable::run);
    });
    var context = box.context;
    assertAll(
        () -> assertThrows(IllegalActorStateException.class, () -> context.currentActor(Runnable.class)),
        () -> assertThrows(IllegalActorStateException.class, () -> context.spawn(Actor.of(Runnable.class).behavior(ctx -> () -> {}))),
        () -> assertThrows(IllegalActorStateException.class, context::shutdown)
    );
  }

  @Test
  public void runNull() {
    var actor = Actor.of(Runnable.class)
        .behavior(context -> context::shutdown);
    assertAll(
        () -> assertThrows(NullPointerException.class, () -> Actor.run(List.of(actor), null)),
        () -> assertThrows(NullPointerException.class, () -> Actor.run(null, context -> {}))
    );
  }

  @Test
  public void runAllWrongThread() {
    var actor = Actor.of(Runnable.class)
        .behavior(context -> () -> {});
    new Thread(() -> {
      assertThrows(IllegalActorStateException.class, () -> Actor.run(List.of(actor), startContext -> {}));
    }).start();
  }

  @Test
  public void runWithTwoActorsWithSignal() throws InterruptedException {
    interface Dummy {
      void execute();
    }
    var actor1 = Actor.of(Dummy.class);
    var actor2 = Actor.of(Dummy.class);
    actor1.behavior(context -> () -> context.postTo(actor2, Dummy::execute));
    actor2
        .behavior(context -> context::shutdown)
        .onSignal((signal, context) -> {
          context.signal(actor1, ShutdownSignal.INSTANCE);
        });
    Actor.run(List.of(actor1, actor2), context -> {
      context.postTo(actor1, Dummy::execute);
    });
  }

  @Test
  public void runWithTwoActorsWithPostTo() throws InterruptedException {
    interface Ops1 {
      void execute();
      void stop();
    }
    interface Ops2 {
      void execute();
    }
    var actor1 = Actor.of(Ops1.class);
    var actor2 = Actor.of(Ops2.class);
    actor1.behavior(context -> new Ops1() {
      @Override
      public void execute() {
        context.postTo(actor2, Ops2::execute);
      }

      @Override
      public void stop() {
        context.shutdown();
      }
    });
    actor2
        .behavior(context -> context::shutdown)
        .onSignal((signal, context) -> {
          context.postTo(actor1, Ops1::stop);
        });
    Actor.run(List.of(actor1, actor2), context -> {
      context.postTo(actor1, Ops1::execute);
    });
  }

  @Test
  public void runAndRestart() throws InterruptedException {
    class Box {
      int result;
    }
    interface Ops {
      void execute(int value);
      void result(Box box);
    }
    var shouldRestart = new AtomicBoolean(true);
    var actor = Actor.of(Ops.class)
        .behavior(context -> new Ops() {
          private int sum;

          @Override
          public void execute(int value) {
            if (value < 0) {
              throw context.panic(new Exception("oops"));
            }
            sum += value;
          }

          @Override
          public void result(Box box) {
            box.result = sum;
          }
        })
        .onSignal((signal, context) -> {
          if (shouldRestart.get()) {
            shouldRestart.set(false);
            context.restart();
          }
        });
    var box = new Box();
    Actor.run(List.of(actor), context -> {
      context.postTo(actor, $ -> $.execute(10));
      context.postTo(actor, $ -> $.execute(-13));
      try {
        Thread.sleep(1_000);
      } catch (InterruptedException e) {
        throw new AssertionError(e);
      }
      context.postTo(actor, $ -> $.execute(32));
      context.postTo(actor, $ -> $.result(box));
      context.postTo(actor, $ -> $.execute(-101));
    });
    assertEquals(32, box.result);
  }

  @Test
  public void runAndSpawn() throws InterruptedException {
    interface Behavior {
      void execute();
      void done();
    }
    interface Behavior2 {
      void execute(int value);
    }
    var actor = Actor.of(Behavior.class)
        .behavior(context -> new Behavior() {
          @Override
          public void execute() {
            var actor2 = Actor.of(Behavior2.class)
                .behavior(context -> value -> assertEquals(42, value));
            context.spawn(actor2);
            context.postTo(actor2, $ -> $.execute(42));
          }

          @Override
          public void done() {
            context.shutdown();  // should also shutdown actor2 !
          }
        });
    Actor.run(List.of(actor), context -> {
      context.postTo(actor, Behavior::execute);
      context.postTo(actor, Behavior::done);
    });
  }
}