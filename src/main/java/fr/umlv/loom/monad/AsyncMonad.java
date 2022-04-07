package fr.umlv.loom.monad;

import java.time.Instant;
import java.util.Collection;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A synchronous monadic API to execute asynchronous tasks.
 *
 * The API is separated into 3 different phases
 * <ol>
 *   <li>Creating the async monad and spawning the tasks with
 *       {@link AsyncMonad#of(Consumer)} or its convenient alternative {@link AsyncMonad#of(Collection)}
 *   <li>Configuring the async monad semantics with
 *       {@link AsyncMonad#unordered()}, {@link AsyncMonad#recover(ExceptionHandler)} and
 *       {@link AsyncMonad#deadline(Instant)}.
 *   <li>Gathering the results of the tasks with
 *       {@link AsyncMonad#result(Function)}.
 * </ol>
 *
 * Here is a simple example
 * <pre>
 *   int sum;
 *   try(var asyncMonad = AsyncMonad.&lt;Integer, RuntimeException&gt;of(forker -&gt; {
 *       forker.fork(() -&gt; {
 *         Thread.sleep(500);
 *         return 500;
 *       });
 *       forker.fork(() -&gt; {
 *         Thread.sleep(100);
 *         return 100;
 *       });
 *     })) {
 *       sum = asyncMonad.result(stream -> stream.mapToInt(v -> v).sum());
 *   }
 * </pre>
 *
 * @param <R> type of task values
 * @param <E> type of the checked exception or {@link RuntimeException} otherwise
 */
public sealed interface AsyncMonad<R, E extends Exception> extends AutoCloseable permits AsyncMonadImpl {
  /**
   * Task to execute.
   * @param <R> type of the return value
   * @param <E> type of the checked exception or {@link RuntimeException} otherwise
   */
  interface Task<R, E extends Exception> {
    /**
     * Compute a value.
     * @return the result of the computation
     * @throws E a checked exception
     * @throws InterruptedException if the task is interrupted
     */
    R compute() throws E, InterruptedException;
  }

  /**
   * Spawn a task asynchronously
   * @param <R> type of the result of a computation
   * @param <E> type of the checked exception or {@link RuntimeException} otherwise
   */
  interface TaskForker<R, E extends Exception> {
    /**
     * Spawn a task asynchronously
     * @param task the task to spawn
     */
    void fork(Task<R, E> task);
  }

  /**
   * Creates an async monad by spawning several tasks.
   * This async monad tracks the order of the spawned tasks so the {@link #result(Function)}s of
   * the asynchronous tasks are processed in order.
   *
   * @param taskForkerConsumer a consumer of {@link TaskForker}
   * @return a newly created async monad
   * @param <R> type of task values
   * @param <E> type of the checked exception or {@link RuntimeException} otherwise
   * @see #unordered()
   */
  static <R, E extends Exception> AsyncMonad<R,E> of(Consumer<? super TaskForker<R, E>> taskForkerConsumer) {
    return AsyncMonadImpl.of(taskForkerConsumer);
  }

  /**
   * Creates an async monad from a collection of tasks.
   * If the collection is {@link Spliterator#ORDERED ordered}, the resulting async monad is ordered too.
   *
   * @param tasks a collection of tasks
   * @return a newly created async monad
   * @param <R> type of task values
   * @param <E> type of the checked exception or {@link RuntimeException} otherwise
   * @see #unordered()
   */
  static <R, E extends Exception> AsyncMonad<R,E> of(Collection<? extends Task<R, E>> tasks) {
    var isOrdered = tasks.spliterator().hasCharacteristics(Spliterator.ORDERED);
    var asyncMonad = AsyncMonad.<R, E>of(consumer -> {
      for (var task : tasks) {
        consumer.fork(task);
      }
    });
    return isOrdered? asyncMonad: asyncMonad.unordered();
  }

  /**
   * Ask the async monad to forget the order, the first results will be the ones of
   * the tasks that finish firsts.
   * This method is an intermediary method that configure the async monad,
   * the handler will be used only when {@link #result(Function)} will be called.
   * @return a new async monad configured to do not process the async task in order
   */
  AsyncMonad<R,E> unordered();

  /**
   * A handler of checked exception.
   * @param <E> type of the exception raised by the tasks
   * @param <R> type of the result of a task
   * @param <F> type of new exception if a new exception is raised, {@link RuntimeException} otherwise
   */
  interface ExceptionHandler<E extends Exception, R, F extends Exception> {
    /**
     * Called to react to a checked exception
     * @param exception the checked exception raised by a task
     * @return the value that is used as result instead of the exception
     * @throws F the new exception raised
     */
    R handle(E exception) throws F;
  }

  /**
   * Installs an exception handler to recover from the checked exceptions raised by the tasks.
   * This method can not intercept {@link RuntimeException} or {@link Error}
   * those will stop the method {@link #result(Function)} to complete.
   * This method is an intermediary method that configure the async monad,
   * the handler will be used only when {@link #result(Function)} will be called.
   *
   * @param handler the exception handler
   * @return a new async monad with the exception handler configured
   * @param <F> the type of the new exception if the exception raised by a task is wrapped
   * @throws IllegalStateException if an exception handler is already configured
   */
  <F extends Exception> AsyncMonad<R,F> recover(ExceptionHandler<? super E, ? extends R, ? extends F> handler);

  /**
   * Specify a deadline for the whole computation.
   * If the deadline time is less than the current time, the deadline is ignored.
   * @param deadline the timeout deadline
   * @return a new async monad configured with the deadline specified
   * @throws IllegalStateException if a deadline is already configured
   * 
   * @see #result(Function)
   * @see DeadlineException
   */
  AsyncMonad<R,E> deadline(Instant deadline);

  /**
   * Exception thrown if the {@link #deadline(Instant) deadline} is reached.
   */
  final class DeadlineException extends RuntimeException {
    /**
     * DeadlineException with a message.
     * @param message the message of the exception
     */
    public DeadlineException(String message) {
      super(message);
    }

    /**
     * DeadlineException with a message and a cause.
     * @param message the message of the exception
     * @param cause the cause of the exception
     */
    public DeadlineException(String message, Throwable cause) {
      super(message, cause);
    }

    /**
     * DeadlineException with a cause.
     * @param cause the cause of the exception
     */
    public DeadlineException(Throwable cause) {
      super(cause);
    }
  }

  /**
   * Propagates the result of the asynchronous tasks as a stream to process them.
   * This method may block if the stream requires elements that are not yet available.
   *
   * Independently of the fact that this method complete normally or not,
   * all tasks either complete normally or are cancelled because the result value is not necessary
   * for the stream computation.
   *
   * @param streamMapper function called with a stream to return the result of the whole computation
   * @return the result of the whole computation
   * @param <T> the type of the result
   * @throws E the type of the checked exception, {@link RuntimeException} otherwise
   * @throws InterruptedException if the current thread or any threads running a task is interrupted
   * @throws DeadlineException if the {@link #deadline(Instant) deadline} is reached
   */
  <T> T result(Function<? super Stream<R>, T> streamMapper) throws E, DeadlineException, InterruptedException;

  /**
   * Closes the async monad and make sure that any dangling asynchronous tasks are cancelled
   * if necessary.
   */
  @Override
  void close();
}
