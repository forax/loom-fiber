package fr.umlv.loom.actor;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * An actor library lika Akka or Erlang.
 *
 * This library supports either a static description of the actor graph using the method
 * {@link #run(List, StartupConsumer)} or a more dynamic approach by {@link Context#spawn(Actor) spawning}
 * actors from a parent actor.
 *
 * <p>
 * This actor library has a special API, it uses an interface to describe the
 * {@link #behavior(Function) behavior} of an actor i.e all the possible messages that can be received
 * and the implementations of those {@link Message messages} are lambdas.
 *
 * <p>
 * An actor is defined by
 * <ul>
 *   <li>a {@link Actor#name() name}</li>
 *   <li>a {@link Actor#state() state}, either {@link State#CREATED CREATED},
 *   {@link State#RUNNING RUNNING} or {@link State#SHUTDOWN SHUTDOWN}.</li>
 *   <li>a restartable {@link Actor#behavior(Function) behavior}.</li>
 *   <li>{@link Actor#onSignal(SignalHandler) signal handlers} to react to a {@link Signal signal} that stop
 *       the actor, either a {@link ShutdownSignal shutdown signal} or a {@link PanicSignal panic exception}.</li>
 * </ul>
 *
 * The library defines 3 different contexts
 * <ol>
 *   <li>the startup context, inside the consumer of {@link Actor#run(List, StartupConsumer) run}, the actions
 *   available are {@link StartupContext#postTo(Actor, Message) postTo} and {@link Context#spawn(Actor) spawn}.</li>
 *   <li>the actor context, inside the {@link Actor#behavior(Function) behavior} of an actor, the actions available
 *   are {@link Context#currentActor(Class) currentActor}, {@link Context#panic(Exception) panic},
 *   {@link Context#postTo(Actor, Message) postTo}, {@link Context#spawn(Actor) spawn} and
 *   {@link Context#shutdown() shutdown}.</li>
 *   <li>the handler context, inside the {@link Actor#onSignal(SignalHandler) signal handler}, the actions available
 *   are {@link HandlerContext#postTo(Actor, Message) postTo}, {@link HandlerContext#restart() restart}  and
 *   {@link HandlerContext#signal(Actor, Signal) signal}.</li>
 * </ol>
 *
 * The actor and its behavior are declared separately. {@link #of(Class, String) Actor.of()} creates an actor,
 * and {@link #behavior(Function) behavior(factory)} associates the behavior to an actor.
 * <pre>
 * record Hello(Context context) {
 *   public void say(String message) {
 *    System.out.println("Hello " + message);
 *   }
 *
 *   public void end() {
 *     context.shutdown();
 *   }
 * }
 *
 * var hello = Actor.of(Hello.class);
 * hello.behavior(Hello::new);
 * </pre>
 *
 * To run as a static configuration, the method {@link #run(List, StartupConsumer)} takes a list of
 * actors and start them. Here, we send a message "say" to the actor "hello" and message "end"
 * to ask the actor "hello" to gently shutdown itself.
 * <pre>
 * Actor.run(List.of(hello), context -> {
 *     context.postTo(hello, $ -> $.say("actors using loom"));
 *     context.postTo(hello, $ -> $.end());
 * });
 * </pre>
 *
 * We may also want to spawn the actor "hello" dynamically, for that let's define two others actors,
 * the actor "manager" that will create an actor "hello" dynamically and the actor "callback" that will
 * receive as parameter the actor "hello" and call it with a message "say".
 * <pre>
 * record Manager(Context context) {
 *   public void createHello(Actor&lt;Callback&gt; callback) {
 *     var hello = Actor.of(Hello.class)
 *           .behavior(Hello::new);
 *     context.spawn(hello);
 *     context.postTo(callback, $ -> $.callHello(hello));
 *   }
 *
 *   public void end() {
 *     context.shutdown();
 *   }
 * }
 *
 * record Callback(Context context) {
 *   public void callHello(Actor&lt;Hello&gt; hello) {
 *     context.postTo(hello, $ -> $.say("actor using loom"));
 *   }
 * }
 * </pre>
 *
 * In the method "createHello", we create the actor "hello", spawn it and calls the callback with the actor.
 * We also register with {@link #onSignal(SignalHandler) onSignal(handler)} the fact that if the actor "manager"
 * is shutdown, the actor "callback" should be shutdown too.
 * <pre>
 * var callback = Actor.of(Callback.class)
 *     .behavior(Callback::new);
 * var manager = Actor.of(Manager.class)
 *     .behavior(Manager::new)
 *     .onSignal((signal, context) -> context.signal(callback, ShutdownSignal.INSTANCE));
 * </pre>
 *
 * To finish, we run the two actors "manager" and "callback", post a message "createHello" and
 * then ask to shut down the manager. This will shut down the actor "hello" because it's a child of "manager"
 * and the actor "callback" because we have registered a signal handler to shut down it.
 * <pre>
 * Actor.run(List.of(manager, callback), context -> {
 *     context.postTo(manager, $ -> $.createHello(callback));
 *     context.postTo(manager, $ -> $.end());
 * });
 * </pre>
 *
 * @param <B> type of the behavior
 */
public final class Actor<B> {
  private static final VarHandle ACTOR_COUNTER, UNCAUGHT_EXCEPTION_HANDLER, STATE, SIGNAL_COUNTER;
  static {
    var lookup = MethodHandles.lookup();
    try {
      ACTOR_COUNTER = lookup.findStaticVarHandle(Actor.class, "actorCounter", int.class);
      UNCAUGHT_EXCEPTION_HANDLER = lookup.findStaticVarHandle(Actor.class, "uncaughtExceptionHandler", UncaughtExceptionHandler.class);
      STATE = lookup.findVarHandle(Actor.class, "state", State.class);
      SIGNAL_COUNTER = lookup.findVarHandle(Actor.class, "signalCounter", int.class);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }
  private static final UncaughtExceptionHandler DEFAULT_UNCAUGHT_EXCEPTION_HANDLER = (actor, exception) -> {
    System.err.println("In actor " + actor.name + ":");
    exception.printStackTrace();
  };
  private static volatile UncaughtExceptionHandler uncaughtExceptionHandler = DEFAULT_UNCAUGHT_EXCEPTION_HANDLER;
  private static volatile int actorCounter = 1;
  private static final ScopeLocal<Actor<?>> CURRENT_ACTOR = ScopeLocal.newInstance();
  private final Thread ownerThread;
  private final Class<B> behaviorType;
  private final String name;
  private final LinkedBlockingQueue<Message<? super B>> mailbox = new LinkedBlockingQueue<>();
  private volatile int signalCounter;  // grow monotonically so the registered handlers are in the right order
  private final ConcurrentSkipListMap<Integer, SignalHandler> signalHandlers = new ConcurrentSkipListMap<>();
  private /*stable*/ Function<? super Context, ? extends B> behaviorFactory;
  private volatile State state = State.CREATED;

  /**
   * State of an actor
   */
  public enum State {
    /**
     * state of the actor after calling {@link #of(Class, String)}
     */
    CREATED,
    /**
     * state of the actor when running either after calling {@link #run(List, StartupConsumer)}
     * or {@link Context#spawn(Actor)}.
     */
    RUNNING,
    /**
     * state of the actor after calling  {@link Context#shutdown()}.
     */
    SHUTDOWN
  }

  /**
   * Used when the state of the actor is incompatible with the operation.
   */
  public static class IllegalActorStateException extends RuntimeException {
    /**
     * Create an IllegalActorStateException with a message
     * @param message a message
     */
    public IllegalActorStateException(String message) {
      super(message);
    }

    /**
     * Create an IllegalActorStateException with a cause
     * @param cause a cause
     */
    public IllegalActorStateException(Throwable cause) {
      super(cause);
    }

    /**
     * Create an IllegalActorStateException with a message and a cause
     * @param message a message
     * @param cause a cause
     */
    public IllegalActorStateException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /**
   * A message that can be post to the mailbox of an actor.
   * @param <B> type of behavior of an actor
   *   
   * @see Context#postTo(Actor, Message)
   */
  @FunctionalInterface
  public interface Message<B> {
    void accept(B behavior) throws Exception;
  }

  public interface StartupConsumer<X extends Exception> {
    void accept(StartupContext context) throws X;
  }

  /**
   * A signal with two possible implementation {@link ShutdownSignal} and {@link PanicSignal}.
   */
  public sealed interface Signal { }

  /**
   * A shutdown signal
   */
  public enum ShutdownSignal implements Signal {
    /**
     * The singleton instance of a ShutdownSignal.
     */
    INSTANCE
  }

  /**
   * A signal containing an exception
   */
  public record PanicSignal(Exception exception) implements Signal {
    public PanicSignal {
      Objects.requireNonNull(exception);
    }
  }

  /**
   * Method called when an exception or a shutdown signal is raised.
   *
   * @see #onSignal(SignalHandler)
   */
  @FunctionalInterface
  public interface SignalHandler {
    /**
     * Method called with a signal and a context that can be used to try to recover on the signal.
     * @param signal the signal
     * @param context the context
     */
    void handle(Signal signal, HandlerContext context);
  }

  /**
   * Actions that can be done at startup after the first actors are running
   * @see #run(List, StartupConsumer) 
   */
  public sealed interface StartupContext {
    /**
     * Post a new message to an actor.
     * @param actor the actor that will receive the message
     * @param message the message
     * @param <B> the type of the behavior
     */
    <B> void postTo(Actor<B> actor, Message<? super B> message);

    /**
     * Dynamically spawn an actor.
     *
     * @param actor the actor
     * @throws IllegalActorStateException if the actor was created by another thread
     */
    void spawn(Actor<?> actor);
  }

  /**
   * Context used by a signal handler.
   *
   * @see SignalHandler
   */
  public sealed interface HandlerContext {
    /**
     * Post a new message to an actor.
     * @param actor the actor that will receive the message
     * @param message the message
     * @param <B> the type of the behavior
     */
    <B> void postTo(Actor<B> actor, Message<? super B> message);

    /**
     * Restart the current actor, cleaning the message queue and resetting the behavior
     * to a fresh one.
     *
     * @see Actor#behavior(Function)
     */
    void restart();

    /**
     * Signal a terminaison event to another actor.
     * @param actor the actor to signal
     * @param signal the signal to send
     * @throws IllegalActorStateException if there is no current actor or
     *   if the current actor is the actor that should receive the signal
     */
    void signal(Actor<?> actor, Signal signal);
  }

  /**
   * Actions that can be done inside the behavior of an actor.
   * @see #behavior(Function)
   */
  public sealed interface Context {
    /**
     * Returns the current actor.
     * @param behaviorType the type of the behavior
     * @return the current actor
     * @param <B> the type of the behavior
     * @throws IllegalActorStateException if there is no current actor
     */
    <B> Actor<B> currentActor(Class<B> behaviorType);

    /**
     * Stop the execution of the actor with an exception.
     * This method pretend to return an error so it can be used in front of a "throw"
     * to explain to the compiler that the control flow stop.
     * <pre>
     *   DatabaseException exception = ...
     *   throw context.panic(exception);
     * </pre>
     * @param exception the exception
     */
    Error panic(Exception exception);

    /**
     * Post a new message to an actor.
     * @param actor the actor that will receive the message
     * @param message the message
     * @param <B> the type of the behavior
     */
    <B> void postTo(Actor<B> actor, Message<? super B> message);

    /**
     * Dynamically spawn a new child actor of the current actor.
     * When shutdown, the current actor will shut down all children actors first.
     *
     * @param actor the child actor
     * @throws IllegalActorStateException if the child actor was not created by the current actor
     */
    void spawn(Actor<?> actor);

    /**
     * Process all messages of the mailbox, shutdown the children of the current actor and
     * then itself.
     * @throws IllegalActorStateException if there is no current actor
     */
    void shutdown();
  }

  /**
   * Called when an exception occurs.
   * This handler should be used for logging purpose only.
   *
   * @see #uncaughtExceptionHandler(UncaughtExceptionHandler)
   */
  @FunctionalInterface
  public interface UncaughtExceptionHandler {
    /**
     * @param actor the actor generating the exception
     * @param exception the exception
     */
    void uncaughtException(Actor<?> actor, Exception exception);
  }

  private final static class SignalMessage implements Message<Object> {
    private final Signal signal;
    private volatile boolean done;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    public SignalMessage(Signal signal, boolean done) {
      this.signal = signal;
      this.done = done;
    }

    public void done() {
      if (!done) {
        lock.lock();
        try {
          done = true;
          condition.signal();
        } finally {
          lock.unlock();
        }
      }
    }

    public void join() {
      InterruptedException interrupted = null;
      lock.lock();
      try {
        while(!done) {
          try {
            condition.await();
          } catch (InterruptedException e) {
            interrupted = e;
          }
        }
      } finally {
        lock.unlock();
      }
      if (interrupted != null) {
        Thread.currentThread().interrupt();
      }
    }

    @Override
    public void accept(Object o) {
      throw new AssertionError("shutdown");
    }
  }

  private static final class PanicError extends Error {
    public PanicError(Exception cause) {
      super(cause);
    }

    @Override
    public Exception getCause() {
      return (Exception) super.getCause();
    }
  }

  private static final class RestartError extends Error {
    public RestartError() {
      super(null, null, false, false);
    }
  }

  private static final class ContextImpl implements Context, StartupContext, HandlerContext {
    public <B> Actor<B> currentActor(Class<B> behaviorType) {
      return currentActor().asActor(behaviorType);
    }

    private Actor<?> currentActor() {
      if (!CURRENT_ACTOR.isBound()) {
        throw new IllegalActorStateException("no current actor");
      }
      return CURRENT_ACTOR.get();
    }

    @Override
    public Error panic(Exception exception) {
      Objects.requireNonNull(exception);
      throw new PanicError(exception);
    }

    @Override
    public <B> void postTo(Actor<B> actor, Message<? super B> message) {
      Objects.requireNonNull(actor);
      Objects.requireNonNull(message);
      actor.mailbox.offer(message);
    }

    @Override
    public void spawn(Actor<?> actor) {
      Objects.requireNonNull(actor);
      actor.checkOwner();
      if (actor.behaviorFactory == null) {
        throw new IllegalActorStateException(actor.name + " behavior is not defined");
      }
      if (CURRENT_ACTOR.isBound()) {
        var currentActor = CURRENT_ACTOR.get();
        // shutdown all children
        var key = currentActor.addSignalHandler((__, handlerContext) -> handlerContext.signal(actor, ShutdownSignal.INSTANCE));
        // remove the handler if the child is shutdown
        actor.addSignalHandler((_1, _2) -> currentActor.removeSignalHandler(key));
      }
      startThread(this, actor);
    }

    @Override
    public void shutdown() {
      var currentActor = currentActor();
      var shutdownConsumer = new SignalMessage(ShutdownSignal.INSTANCE, true);  // async
      postTo(currentActor, shutdownConsumer);
    }

    @Override
    public void restart() {
      throw new RestartError();
    }

    @Override
    public void signal(Actor<?> actor, Signal signal) {
      Objects.requireNonNull(actor);
      Objects.requireNonNull(signal);
      var currentActor = currentActor();
      if (actor == currentActor) {
        throw new IllegalActorStateException("an actor can not signal itself");
      }
      var signalConsumer = new SignalMessage(signal, false);  // synchronous
      postTo(actor, signalConsumer);
      signalConsumer.join();
    }
  }

  private Actor(Thread ownerThread, Class<B> behaviorType, String name) {
    this.ownerThread = ownerThread;
    this.behaviorType = behaviorType;
    this.name = name;
  }

  /**
   * Creates an actor with a name.
   * @param behaviorType the type of the behavior
   * @param name the actor name
   * @param <B> the type of the behavior.
   * @return a new actor
   */
  public static <B> Actor<B> of(Class<B> behaviorType, String name) {
    Objects.requireNonNull(behaviorType);
    Objects.requireNonNull(name);
    return new Actor<>(Thread.currentThread(), behaviorType, name);
  }

  /**
   * Creates an actor with a name derived from the name of the behavior type.
   * @param behaviorType the type of the behavior
   * @param <B> the type of the behavior.
   * @return a new actor
   */
  public static <B> Actor<B> of(Class<B> behaviorType) {
    return of(behaviorType, behaviorType.getSimpleName() + (int) ACTOR_COUNTER.getAndAdd(1));
  }

  /**
   * Returns the name of the actor.
   * The name can be any arbitrary name and is only useful for debugging purpose.
   * @return the name of the actor.
   * 
   * @see Actor#of(Class, String) 
   */
  public String name() {
    return name;
  }

  /**
   * Returns the behavior type.
   * @return the behavior type
   * 
   * {@link Actor#of(Class, String)}
   */
  public Class<B> behaviorType() {
    return behaviorType;
  }

  @Override
  public String toString() {
    return "Actor(" + name + ")";
  }

  /**
   * Returns the state of the actor
   * @return the state of the actor
   */
  public State state() {
    return state;
  }

  @SuppressWarnings("unchecked")
  private <C> Actor<C> asActor(Class<C> behaviorType) {
    Objects.requireNonNull(behaviorType);
    if (!behaviorType.isAssignableFrom(this.behaviorType)) {
      throw new IllegalActorStateException(name + " does not allow behavior " + behaviorType.getName());
    }
    return (Actor<C>) this;
  }

  private int addSignalHandler(SignalHandler signalHandler) {
    var key = (int) SIGNAL_COUNTER.getAndAdd(this, 1);
    signalHandlers.put(key, signalHandler);
    return key;
  }

  private void removeSignalHandler(int key) {
    signalHandlers.remove(key);
  }

  private static void logException(Actor<?> actor, Exception exception) {
    try {
      uncaughtExceptionHandler.uncaughtException(actor, exception);
    } catch (Exception e) {
      // the global exception handler fails !
      e.addSuppressed(exception);
      e.printStackTrace();
    }
  }

  private static void signalNow(Signal signal, ContextImpl context, Actor<?> actor) {
    actor.state = State.SHUTDOWN;
    for (var handler : actor.signalHandlers.values()) {
      try {
        handler.handle(signal, context);
      } catch (Exception e) {
        logException(actor, new IllegalActorStateException("error in signal handler", e));
      }
    }
    actor.signalHandlers.clear();
  }

  private static <B> Thread startThread(ContextImpl context, Actor<B> actor) {
    if (!STATE.compareAndSet(actor, State.CREATED, State.RUNNING)) {
      throw new IllegalActorStateException("actor is already running/shutdown");
    }
    //return Thread.ofPlatform().name(actor.name).start(() -> {
    return Thread.ofVirtual().name(actor.name).start(() -> {
      ScopeLocal.where(CURRENT_ACTOR, actor, () -> {
        var behavior = actor.behaviorFactory.apply(context);
        for (;;) {
          try {
            Message<? super B> message;
            try {
              message = actor.mailbox.take();
            } catch (InterruptedException interruptedException) {
              signalNow(new PanicSignal(interruptedException), context, actor);
              return;
            }
            if (message instanceof SignalMessage signalMessage) {
              try {
                signalNow(signalMessage.signal, context, actor);
              } finally {
                signalMessage.done();
              }
              return;
            }
            try {
              message.accept(behavior);
            } catch (Exception | PanicError e) {
              var exception = e instanceof PanicError panicError ? panicError.getCause() : (Exception) e;
              logException(actor, exception);
              signalNow(new PanicSignal(exception), context, actor);
              return;
            }
          } catch(RestartError restartError) {
            actor.mailbox.clear();
            behavior = actor.behaviorFactory.apply(context);
          }
        }
      });
    });
  }

  private static void joinAll(List<Thread> threads) throws InterruptedException {
    for (var thread : threads) {
      thread.join();
    }
  }

  private void checkOwner() {
    if (ownerThread != Thread.currentThread()) {
      throw new IllegalActorStateException(name + " is not created by the current thread");
    }
  }

  private void checkStateCreated() {
    if (state != State.CREATED) {
      throw new IllegalActorStateException(name + " is already running/shutdown");
    }
  }

  /**
   * Set the behavior of an actor.
   * @param behavior the actor's behavior
   * @return the current actor so method calls can be chained
   * @throws IllegalActorStateException if the current thread is not the one that have created the actor,
   *   if the actor is already running/shutdown or if the actor's behavior is already set
   */
  public Actor<B> behavior(Function<? super Context, ? extends B> behavior) {
    Objects.requireNonNull(behavior);
    checkOwner();
    checkStateCreated();
    if (this.behaviorFactory != null) {
      throw new IllegalActorStateException("behavior() can only called once");
    }
    this.behaviorFactory = behavior;
    return this;
  }

  /**
   * Register a code to execute when a signal occurs
   * @param handler the code to execute
   * @return the current actor so method calls can be chained
   * @throws IllegalActorStateException if the current thread is not the owner thread of the actor or
   *   if the actor is already running/shutdown
   *
   * @see Context#shutdown()
   * @see HandlerContext#signal(Actor, Signal)
   */
  public Actor<B> onSignal(SignalHandler handler) {
    Objects.requireNonNull(handler);
    checkOwner();
    checkStateCreated();
    addSignalHandler(handler);
    return this;
  }

  /**
   * Starts all the actors, {@link StartupContext#postTo(Actor, Message) post} some messages
   * and wait until all actors have been shutdown.
   * @param actors a list of actors to start
   * @param consumer a code that can post messages
   * @throws InterruptedException if the current thread is interrupted
   * @throws IllegalActorStateException if the current thread is not the one that have created the actors
   *   if an actor is already running/shutdown or if an actor has no behavior
   */
  public static <X extends Exception> void run(List<? extends Actor<?>> actors, StartupConsumer<? extends X> consumer)
      throws InterruptedException, X {
    Objects.requireNonNull(actors);
    Objects.requireNonNull(consumer);
    for(Actor<?> actor: actors) {
      actor.checkOwner();
      if (actor.behaviorFactory == null) {
        throw new IllegalActorStateException(actor.name + " behavior is not defined");
      }
    }
    var context = new ContextImpl();
    var threads = actors.stream()
        .map(actor -> startThread(context, actor))
        .toList();
    consumer.accept(context);
    joinAll(threads);
  }

  /**
   * Set the global exception handler.
   * This handler should be used to log exceptions, not to try to recover exception
   * @param uncaughtExceptionHandler exception handler called when an actor behavior throw an exception
   * @throws IllegalActorStateException if the global exception handler has already been set
   *
   * @see #onSignal(SignalHandler)
   */
  public static void uncaughtExceptionHandler(UncaughtExceptionHandler uncaughtExceptionHandler) {
    Objects.requireNonNull(uncaughtExceptionHandler);
    if (!UNCAUGHT_EXCEPTION_HANDLER.compareAndSet(DEFAULT_UNCAUGHT_EXCEPTION_HANDLER, uncaughtExceptionHandler)) {
      throw new IllegalActorStateException("uncaught exception handler already set");
    }
  }
}

