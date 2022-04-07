package fr.umlv.loom.monad;

import java.time.Instant;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * A scope to execute asynchronous tasks in way that appears to be synchronous.
 *
 * The API is separated into 3 different phases
 * <ol>
 *   <li>Create the async scope with {@link AsyncScope#of(UnaryOperator)} using the option
 *       {@link Option#unordered()} or {@link Option#deadline(Instant)}. The convenient method
 *       {@link AsyncScope#of()} can be used if there are no option.
 *   <li>Optionally configure how to react to checked exception using
 *        {@link AsyncScope#recover(ExceptionHandler)}
 *   <li>Gather the results of the tasks with
 *       {@link AsyncScope#result(Function)}.
 * </ol>
 *
 * The default semantics is the same as a loop over all the tasks and calling them one by one
 * to get the results. Thus, the results are available in order, exceptions are propagated as
 * usual and if an exception occurs it cancels all the remaining tasks.
 *
 * The method {@link Option#unordered()} relax the ordering constraint allowing the results to be processed
 * as soon as they are available. The method {@link #recover(ExceptionHandler)} relax the exception constraint
 * for checked exceptions (exceptions that are not subclasses of {@link RuntimeException})
 * by providing a way to either substitute a value to the exception or wrap the exception into another
 * exception.
 *
 * Here is a simple example
 * <pre>
 *   List&lt;Integer&gt; list;
 *   try(var scope = AsyncScope.&lt;Integer, RuntimeException&gt;of()) {
 *       scope.fork(() -&gt; 10);
 *       scope.fork(() -&gt; 20);
 *       list = scope.result(Stream::toList);  // [10, 20]
 *   }
 * </pre>
 *
 * and an example using the option {@link Option#unordered()}
 * <pre>
 *   List&lt;Integer&gt; list;
 *   try(var scope = AsyncScope.&lt;Integer, RuntimeException&gt;of(opt -> opt
 *            .unordered()
 *   )) {
 *       scope.fork(() -&gt; {
 *         Thread.sleep(1_000);
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
   * Creates an async scope with the default behavior.
   *
   * @param <R> type of task values
   * @param <E> type of the checked exception or {@link RuntimeException} otherwise
   */
  static <R, E extends Exception> AsyncScope<R,E> of() {
    return of(option -> option);
  }

  /**
   * Creates an async scope with options.
   *
   * @param <R> type of task values
   * @param <E> type of the checked exception or {@link RuntimeException} otherwise
   */
  static <R, E extends Exception> AsyncScope<R,E> of(UnaryOperator<Option<R, E>> optionOperator) {
    return AsyncScopeImpl.of(optionOperator);
  }

  /**
   * Spawn a task asynchronously
   * @param <R> type of the result of a computation
   * @param <E> type of the checked exception or {@link RuntimeException} otherwise
   */
  sealed interface Option<R, E extends Exception> permits AsyncScopeImpl.OptionBuilder {
    /**
     * Configures the async scope to forget the order, the first results will be the ones of
     * the tasks that finish firsts.
     * @return the configured option
     */
    Option<R,E> unordered();

    /**
     * Configures a deadline for the whole computation.
     * If the deadline time is less than the current time, the deadline is ignored.
     * @param deadline the timeout deadline
     * @return the configured option
     * @throws IllegalStateException if a deadline is already configured
     *
     * @see #result(Function)
     * @see DeadlineException
     */
    Option<R,E> deadline(Instant deadline);
  }

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
   * @param task the task to spawn
   *             
   * @see #result(Function)
   */
  AsyncScope<R, E> fork(Task<? extends R, ? extends E> task);

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
   * Configures an exception handler to recover from the checked exceptions raised by the tasks.
   * This method can not intercept {@link RuntimeException} or {@link Error}
   * those will stop the method {@link #result(Function)} to complete.
   * This method is an intermediary method that configure the async monad,
   * the handler will be used only when {@link #result(Function)} will be called.
   *
   * @param handler the exception handler
   * @return a new async scope
   * @param <F> the type of the new exception if the exception raised by a task is wrapped
   * @throws IllegalStateException if an exception handler is already configured
   */
  <F extends Exception> AsyncScope<R,F> recover(ExceptionHandler<? super E, ? extends R, ? extends F> handler);

  /**
   * Exception thrown if the {@link Option#deadline(Instant) deadline} is reached.
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
   * Propagates the results of the asynchronous tasks as a stream to process them.
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
   * @throws DeadlineException if the {@link Option#deadline(Instant) deadline} is reached
   */
  <T> T result(Function<? super Stream<R>, T> streamMapper) throws E, DeadlineException, InterruptedException;

  /**
   * Closes the async monad and make sure that any dangling asynchronous tasks are cancelled
   * if necessary.
   */
  @Override
  void close();
}
