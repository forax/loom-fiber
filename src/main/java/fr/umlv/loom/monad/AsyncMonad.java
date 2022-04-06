package fr.umlv.loom.monad;

import java.time.Instant;
import java.util.Collection;
import java.util.Spliterator;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A synchronous monadic API to execute asynchronous computations.
 *
 * <p>
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
 * </p>
 *
 * @param <R> type of task values
 * @param <E> type of the checked exception or {@link RuntimeException} otherwise
 */
public interface AsyncMonad<R, E extends Exception> extends AutoCloseable {
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
   * Object able to spawn a task asynchronously
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
   * This async monad tracks the order of the spawned tasks so the {@link #result(Function)}s will be
   * processed in order.
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
    var asyncMonad = AsyncMonad.<R, E>of(consumer -> {
      for (var task : tasks) {
        consumer.fork(task);
      }
    });
    var isOrdered = tasks.spliterator().hasCharacteristics(Spliterator.ORDERED);
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
   * An exception handler
   * @param <E> type of the exception raised by the tasks
   * @param <R> type of the result of a task
   * @param <F> type of a new exception if the exceptions raside by a task is wrapped
   */
  interface ExceptionHandler<E extends Exception, R, F extends Exception> {
    /**
     * Called to react to an exception
     * @param exception the exception raised by a task
     * @return the value used to swallow the exception
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
   * @param <F> the type of a new exception if the exception raised by a task is wrapped
   * @throws IllegalStateException if an exception handler is already configured
   */
  <F extends Exception> AsyncMonad<R,F> recover(ExceptionHandler<? super E, ? extends R, ? extends F> handler);

  /**
   * Specify a deadline for the whole computation.
   * @param deadline the timeout deadline
   * @return a new async monad configured with the deadline specified
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
   * @return a result
   * @param <T> the type of the result
   * @throws E the type of the checked exception
   * @throws InterruptedException if the current thread or any threads doing a computation is interrupted
   * @throws DeadlineException if the {@link #deadline(Instant) deadline} is reached
   */
  <T> T result(Function<? super Stream<R>, T> streamMapper) throws E, DeadlineException, InterruptedException;

  /**
   * Closes the async monad and make sure that all the dangling asynchronous computations are cancelled
   * if necessary.
   */
  @Override
  void close();
}
