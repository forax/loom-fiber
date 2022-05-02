package fr.umlv.loom.structured;

import java.io.Serializable;
import java.time.Instant;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A scope to execute asynchronous tasks in way that appears to be synchronous.
 *
 * The API is separated into different phases
 * <ol>
 *   <li>Create the async scope with either {@link AsyncScope#ordered()} or
 *       if you don't care about the order with {@link AsyncScope#unordered()}.
 *   <li>Execute task using {@link #async(Task)}
 *   <li>Optionally configure how to react to checked exceptions using
 *        {@link AsyncScope#recover(ExceptionHandler)} or set a deadline with {@link #deadline(Instant)}.
 *   <li>Gather the results of the tasks in a stream with
 *       {@link AsyncScope#await(Function)}.
 * </ol>
 *
 * Using {@link #ordered()} is equivalent to a loop over all the tasks and calling them one by one
 * to get the results. Thus, the results are available in order.
 * Using {@link #unordered()} relax the ordering constraint allowing the results to be processed
 *  * as soon as they are available.
 *
 * Exceptions are automatically propagated from the tasks to the method {@link #await(Function)} and
 * if an exception occurs it cancels all the remaining tasks.
 *
 * The method {@link #recover(ExceptionHandler)} recovers from checked exceptions (exceptions that are
 * not subclasses of {@link RuntimeException}) either by returning a value instead or by
 * propagating another exception.
 *
 * Here is a simple example using {@link #ordered()}, despite the fact that the first
 * task take longer to complete, the results of the tasks are available in order.
 * <pre>
 *   List&lt;Integer&gt; list;
 *   try(var scope = AsyncScope.&lt;Integer, RuntimeException&gt;ordered()) {
 *       scope.async(() -&gt; {
 *         Thread.sleep(200);
 *         return 10;
 *       });
 *       scope.async(() -&gt; 20);
 *       list = scope.await(Stream::toList);  // [10, 20]
 *   }
 * </pre>
 *
 * and an example using {@link #unordered()}, here, the results are available
 * in completion order.
 * <pre>
 *   List&lt;Integer&gt; list;
 *   try(var scope = AsyncScope.&lt;Integer, RuntimeException&gt;unordered()) {
 *       scope.fork(() -&gt; {
 *         Thread.sleep(200);
 *         return 10;
 *       });
 *       scope.fork(() -&gt; 20);
 *       list = scope.result(Stream::toList);  // [20, 10]
 *   }
 * </pre>
 *
 *
 * @param <R> type of task values
 * @param <E> type of the checked exception or {@link RuntimeException} otherwise
 */
public sealed interface AsyncScope<R, E extends Exception> extends AutoCloseable permits AsyncScopeImpl {
  /**
   * Creates an async scope with that receives the results of tasks in the order of the calls to {@link #async(Task)}.
   *
   * @param <R> type of task values
   * @param <E> type of the checked exception or {@link RuntimeException} otherwise
   */
  static <R, E extends Exception> AsyncScope<R,E> ordered() {
    return AsyncScopeImpl.of(true);
  }

  /**
   * Creates an async scope that receives the results of tasks out of order.
   *
   * @param <R> type of task values
   * @param <E> type of the checked exception or {@link RuntimeException} otherwise
   */
  static <R, E extends Exception> AsyncScope<R,E> unordered() {
    return AsyncScopeImpl.of(false);
  }

  /**
   * Task to execute.
   * @param <R> type of the return value
   * @param <E> type of the checked exception or {@link RuntimeException} otherwise
   *           
   * @see #async(Task)
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
   * Execute a task asynchronously.
   * @param task the task to execute
   * @throws IllegalStateException if the lambda captures a value of a mutable class while assertions are enabled
   *             
   * @see #await(Function)
   */
  AsyncScope<R, E> async(Task<? extends R, ? extends E> task);

  /**
   * A handler of checked exceptions.
   * @param <E> type of the exception raised by the tasks
   * @param <R> type of the result of a task
   * @param <F> type of new exception if a new exception is raised, {@link RuntimeException} otherwise
   *
   * @see #recover(ExceptionHandler)
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
   * Configures an exception handler to recover from the checked exceptions potentially raised by the tasks.
   * This method can not intercept {@link RuntimeException} or {@link Error},
   * those will stop the method {@link #await(Function)} to complete.
   * This method is an intermediary method that configure the async scope,
   * the handler will be used when {@link #await(Function)} is called.
   *
   * @param handler the exception handler
   * @return a new async scope
   * @param <F> the type of the new exception if the exception raised by a task is wrapped
   * @throws IllegalStateException if an exception handler is already configured
   */
  <F extends Exception> AsyncScope<R,F> recover(ExceptionHandler<? super E, ? extends R, ? extends F> handler);

  /**
   * Exception thrown if the deadline set by {@link #deadline(Instant) deadline} is reached.
   */
  final class DeadlineException extends RuntimeException {
    /**
     * Creates a DeadlineException with a message.
     * @param message the message of the exception
     */
    public DeadlineException(String message) {
      super(message);
    }

    /**
     * Creates a DeadlineException with a message and a cause.
     * @param message the message of the exception
     * @param cause the cause of the exception
     */
    public DeadlineException(String message, Throwable cause) {
      super(message, cause);
    }

    /**
     * Creates a DeadlineException with a cause.
     * @param cause the cause of the exception
     */
    public DeadlineException(Throwable cause) {
      super(cause);
    }
  }

  /**
   * Configures a deadline for the whole computation.
   * If the deadline time is less than the current time, the deadline is ignored.
   * This method is an intermediary method that configure the async scope,
   * the deadline will be set when {@link #await(Function)} is called.
   *
   * @param deadline the timeout deadline
   * @return the configured option
   * @throws IllegalStateException if a deadline is already configured
   *
   * @see #await(Function)
   * @see DeadlineException
   */
  AsyncScope<R,E> deadline(Instant deadline);

  /**
   * Propagates the results of the asynchronous tasks as a stream to process them.
   * This method may block if the stream requires elements that are not yet available.
   *
   * When this method returns, all tasks either complete normally or are cancelled because
   * the result value is not necessary for the stream computation.
   *
   * @param streamMapper function called with a stream to return the result of the whole computation
   * @return the result of the whole computation
   * @param <T> the type of the result
   * @throws E the type of the checked exception, {@link RuntimeException} otherwise
   * @throws InterruptedException if the current thread or any threads running a task is interrupted
   * @throws DeadlineException if the {@link #deadline(Instant) deadline} is reached
   */
  <T> T await(Function<? super Stream<R>, ? extends T> streamMapper) throws E, DeadlineException, InterruptedException;

  /**
   * Closes the async scope and if necessary make sure that any dangling asynchronous tasks are cancelled.
   * This method is idempotent.
   */
  @Override
  void close();
}
