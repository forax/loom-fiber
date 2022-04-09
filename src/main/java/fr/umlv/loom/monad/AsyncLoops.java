package fr.umlv.loom.monad;

import fr.umlv.loom.monad.AsyncScope.ExceptionHandler;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Abstracts the execution of asynchronous computations that may block using virtual threads
 * as a classical loop using a stream to collect the results of the computations.
 *
 * The method {@link #asyncLoop(Stream, Computation, Function)} takes 3 different parameters,
 * a stream of values, the {@code source}, a computation, a lambda that will call with each value and
 * a lambda that takes a stream of the result of the computation and return a value.
 * The method either returns the return value of the stream of results, or throws an exception if one
 * of the computation failed.
 *
 * By default, the stream of results gets the results in the same order of the source stream.
 * If the source stream is {@link Stream#unordered()} then the results will appear as soon as the computations
 * are finished.
 *
 * If the stream of results does not need to process all the results to provide an answer (if it shortcut),
 * the computations that have not yet produces a result are automatically cancelled by
 * {@link Thread#interrupt() interrupting} the virtual threads that run them..
 *
 * Here is an example with an ordered stream of value, the stream of results is transformed to a list,
 * so the list contains the result in the same order.
 * <pre>
 *   var times = Stream.of(200, 100);
 *   var list = AsyncLoops.asyncLoop(times,
 *     time -> {
 *       Thread.sleep(time);
 *       return time;
 *     },
 *     Stream::toList);
 *   System.out.println(list);   // [200, 100]
 * </pre>
 *
 * Here, we explicitly call {@link Stream#unordered()} on the source stream to ask
 * for rexaling the order constraint. The computation that sleeps for 100 milliseconds
 * finish first and is the result of {@link #asyncLoop(Stream, Computation, Function)}.
 * The other computation is cancelled because the stream returns early.
 * <pre>
 *   var times = Stream.of(500, 100).unordered();
 *   var result = AsyncLoops.asyncLoop(times,
 *     time -> {
 *       Thread.sleep(time);
 *       return time;
 *     },
 *     Stream::findFirst)
 *     .orElseThrow();
 *   System.out.println(result); // 100
 * </pre>
 *
 *
 *
 * A more involved example, we want to find the last modified file
 * of the current folder. {@link #asyncLoop(Stream, Computation, Function)} called
 * {@code Files#getLastModifiedTime()} on all files, the stream process all the times
 * and returns the path with the last modified time.
 * If one call to {@code Files#getLastModifiedTime()} throws an IOException, this exception
 * is propagated by {@link #asyncLoop(Stream, Computation, Function)}.
 * <pre>
 *   Optional&lt;Path&gt; lastModifiedFile;
 *   try(var paths = Files.list(Path.of("."))) {
 *     record PathAndTime(Path path, FileTime time) {}
 *     lastModifiedFile = AsyncLoops.asyncLoop(paths,
 *         path -&gt; {
 *           var time = Files.getLastModifiedTime(path);
 *           return new PathAndTime(path, time);
 *         },
 *         stream -&gt; stream.max(Comparator.comparing(PathAndTime::time))
 *       ).map(PathAndTime::path);
 *   }
 *   System.out.println(lastModifiedFile);
 * </pre>
 *
 * It is also possible to put a time bound for the whole calculation by specifying a deadline
 * using {@link #asyncLoopWithDeadline(Stream, Computation, Function, Instant)}.
 * <pre>
 *    AsyncLoops.asyncLoopWithDeadline(values,
 *      value -> {
 *        Thread.sleep(1_000);
 *        return null;
 *      },
 *      Stream::findFirst,
 *      Instant.now().plus(100, ChronoUnit.MILLIS));
 * </pre>
 */
public class AsyncLoops {
  /**
   * Asynchronous computation that may support blocking operations.
   * @param <V> type of the parameter
   * @param <R> type of the return type
   * @param <E> type of the checked exception or {@code RuntimeException}.
   */
  public interface Computation<V, R, E extends Exception> {
    /**
     * Execute the asynchronous computation.
     *
     * @param value the parameter value
     * @return the resulting value
     * @throws E a checked exception or {@code RuntimeException}.
     * @throws InterruptedException if the computation is interrupted
     */
    R compute(V value) throws E, InterruptedException;
  }

  /**
   * Execute several computation asynchronously from a stream of source and use a stream to collect the results.
   * Each computation is run in its own virtual threads so blocking operations in the computation are allowed.
   * The results of the computation inside the result stream are ordered by default apart if the source stream
   * is unordered.
   * If the result stream is shortcut, all the computations that have yet produced a result are cancelled.
   *
   * @param source the source of all the values
   * @param computation the computation that will be called for each value of the source
   * @param mapper the function called with the stream of results of the computation
   * @return the return value of the stream of results of the computations
   * @param <V> the type of the source values
   * @param <R> the type of the result of the computations
   * @param <T> the result of the loop
   * @param <E> a checked exception or {@code RuntimeException} otherwise.
   * @throws E a checked exception throws by one of the computations
   * @throws InterruptedException if either the current thread is interrupted or one of the virtual thread that
   *         run the computations is interrupted
   */
  public static <V, R, T, E extends Exception> T asyncLoop(Stream<? extends V> source, Computation<? super V, ? extends R, ? extends E> computation, Function<? super Stream<R>, ? extends T> mapper) throws E, InterruptedException {
    Objects.requireNonNull(source);
    Objects.requireNonNull(computation);
    Objects.requireNonNull(mapper);
    try {
      return asyncLoopWithDeadlineImpl(source, computation, mapper, null);
    } catch (TimeoutException e) {
      throw new AssertionError(e);
    }
  }

  /**
   * Execute several computation asynchronously from a stream of source and use a stream to collect the results.
   * Each computation is run in its own virtual threads so blocking operations in the computation are allowed.
   * The results of the computation inside the result stream are ordered by default apart if the source stream
   * is unordered.
   * If the result stream is shortcut, all the computations that have yet produced a result are cancelled.
   * If a deadline is specified,
   *
   * @param source the source of all the values
   * @param computation the computation that will be called for each value of the source
   * @param mapper the function called with the stream of results of the computation
   * @param deadline a deadline
   * @return the return value of the stream of results of the computations
   * @param <V> the type of the source values
   * @param <R> the type of the result of the computations
   * @param <T> the result of the loop
   * @param <E> a checked exception or {@code RuntimeException} otherwise.
   * @throws E a checked exception throws by one of the computations
   * @throws InterruptedException if either the current thread is interrupted or one of the virtual thread that
   *         run the computations is interrupted
   * @throws TimeoutException if the deadline is reached
   */
  public static <V, R, T, E extends Exception> T asyncLoopWithDeadline(Stream<? extends V> source, Computation<? super V, ? extends R, ? extends E> computation, Function<? super Stream<R>, ? extends T> mapper, Instant deadline) throws E, InterruptedException, TimeoutException {
    Objects.requireNonNull(source);
    Objects.requireNonNull(computation);
    Objects.requireNonNull(mapper);
    Objects.requireNonNull(deadline);
    return asyncLoopWithDeadlineImpl(source, computation, mapper, deadline);
  }

  private static <V, R, T, E extends Exception> T asyncLoopWithDeadlineImpl(Stream<? extends V> source, Computation<? super V, ? extends R, ? extends E> computation, Function<? super Stream<R>, ? extends T> mapper, Instant deadline) throws E, InterruptedException, TimeoutException {
    var executorService = Executors.newVirtualThreadPerTaskExecutor();
    var valueSpliterator = source.spliterator();
    //if (!valueSpliterator.hasCharacteristics(Spliterator.SIZED) && valueSpliterator.estimateSize() == Long.MAX_VALUE) {
    //  throw new IllegalStateException("infinite stream are not supported");
    //}
    var ordered = valueSpliterator.hasCharacteristics(Spliterator.ORDERED);
    var completionService = ordered? null: new ExecutorCompletionService<R>(executorService);
    var futures = new ArrayList<Future<R>>();
    valueSpliterator.forEachRemaining(value -> futures.add(completionService != null?
          completionService.submit(() ->  computation.compute(value)):
          executorService.submit(() -> computation.compute(value))));
    var spliterator = ordered?
        orderedSpliterator(executorService, futures, null, deadline):
        unorderedSpliterator(executorService, completionService, futures, null, deadline);
    var stream = StreamSupport.stream(spliterator, false).
        onClose(() -> {
          executorService.shutdownNow();
          try {
            while(!executorService.awaitTermination(1, TimeUnit.DAYS)) {
              // empty
            }
          } catch (InterruptedException e) {
            rethrow(e);
          }
        });
    try(stream) {
      return mapper.apply(stream);
    }
  }

  // Utilities
  // this code is mostly duplicated in AsyncMonadImpl and AsyncScopeImpl

  private static <R, E extends Exception> Spliterator<R> orderedSpliterator(ExecutorService executorService, List<Future<R>> futures, ExceptionHandler<Exception, ? extends R, ? extends E> handler, Instant deadline) {
    return new Spliterator<>() {
      private int index;

      @Override
      public boolean tryAdvance(Consumer<? super R> action) {
        if (index == futures.size()) {
          return false;
        }
        var future = futures.get(index);
        try {
          R result;
          try {
            result = deadline == null?
                future.get():
                future.get(timeoutInNanos(deadline), TimeUnit.NANOSECONDS);
          } catch (ExecutionException e) {
            result = handleException(e.getCause(), handler);
          }
          action.accept(result);
        } catch (Exception e) {
          executorService.shutdownNow();
          throw rethrow(e);
        }
        index++;
        return true;
      }

      @Override
      public Spliterator<R> trySplit() {
        return null;
      }
      @Override
      public long estimateSize() {
        return futures.size() - index;
      }
      @Override
      public int characteristics() {
        return IMMUTABLE | SIZED | ORDERED;
      }
    };
  }

  private static <R, E extends Exception> Spliterator<R> unorderedSpliterator(ExecutorService executorService, ExecutorCompletionService<R> completionService, List<Future<R>> futures, ExceptionHandler<Exception, ? extends R, ? extends E> handler, Instant deadline) {
    return new Spliterator<>() {
      private int index;

      @Override
      public boolean tryAdvance(Consumer<? super R> action) {
        if (index == futures.size()) {
          return false;
        }
        try {
          var future = deadline == null?
              completionService.take():
              completionService.poll(timeoutInNanos(deadline), TimeUnit.NANOSECONDS);
          if (future == null) {
            throw new TimeoutException("deadline reached");
          }
          R result;
          try {
            result = future.get();
          } catch (ExecutionException e) {
            result = handleException(e.getCause(), handler);
          }
          action.accept(result);
          index++;
          return true;
        } catch (Exception e) {
          executorService.shutdownNow();
          throw rethrow(e);
        }
      }

      @Override
      public Spliterator<R> trySplit() {
        return null;
      }
      @Override
      public long estimateSize() {
        return futures.size() - index;
      }
      @Override
      public int characteristics() {
        return IMMUTABLE | SIZED;
      }
    };
  }

  private static long timeoutInNanos(Instant deadline) {
    var timeout = Duration.between(Instant.now(), deadline).toNanos();
    return timeout < 0? 0: timeout;
  }

  private static <R, E extends Exception> R handleException(Throwable cause, ExceptionHandler<Exception, ? extends R, ? extends E> handler) throws InterruptedException {
    if (cause instanceof RuntimeException e) {
      throw e;
    }
    if (cause instanceof Error e) {
      throw e;
    }
    if (cause instanceof InterruptedException e) {
      throw e;
    }
    if (cause instanceof Exception e) {
      if (handler != null) {
        R newValue;
        try {
          newValue = handler.handle(e);
        } catch (Exception newException) {
          throw rethrow(newException);
        }
        return newValue;
      }
    }
    throw rethrow(cause);
  }

  @SuppressWarnings("unchecked")   // very wrong but works
  private static <T extends Throwable> AssertionError rethrow(Throwable cause) throws T {
    throw (T) cause;
  }
}
