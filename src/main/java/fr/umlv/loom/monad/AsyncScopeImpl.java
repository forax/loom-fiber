package fr.umlv.loom.monad;

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
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

record AsyncScopeImpl<R, E extends Exception>(
    ExecutorService executorService,
    ExecutorCompletionService<R> completionService,
    ArrayList<Future<R>> futures,
    ExceptionHandler<Exception, ? extends R, ? extends E> handler,
    Instant deadline
    ) implements AsyncScope<R, E> {
  final static class OptionBuilder<R, E extends Exception> implements Option<R, E> {
    private boolean unordered;
    private Instant deadline;

    @Override
    public Option<R, E> unordered() {
      unordered = true;
      return this;
    }

    @Override
    public Option<R, E> deadline(Instant deadline) {
      Objects.requireNonNull(deadline);
      if (this.deadline != null) {
        throw new IllegalStateException("deadline already set");
      }
      this.deadline = deadline;
      return this;
    }
  }

  public static <R, E extends Exception> AsyncScope<R,E> of(UnaryOperator<Option<R,E>> optionOperator) {
    Objects.requireNonNull(optionOperator, "optionOperator is null");
    var optionBuilder = (OptionBuilder<R, E>) optionOperator.apply(new OptionBuilder<>());
    var executorService = Executors.newVirtualThreadPerTaskExecutor();
    var completionService = optionBuilder.unordered?
        new ExecutorCompletionService<R>(executorService): null;
    return new AsyncScopeImpl<>(executorService, completionService, new ArrayList<>(), null, optionBuilder.deadline);
  }

  @Override
  public AsyncScope<R, E> fork(Task<? extends R, ? extends E> task) {
    if (completionService != null) {
      futures.add(completionService.submit(task::compute));
    } else {
      futures.add(executorService.submit(task::compute));
    }
    return this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <F extends Exception> AsyncScope<R, F> recover(ExceptionHandler<? super E, ? extends R, ? extends F> handler) {
    Objects.requireNonNull(handler);
    if (executorService.isShutdown()) {
      throw new IllegalStateException("result already called");
    }
    if (this.handler != null) {
      throw new IllegalStateException("handler already set");
    }
    return new AsyncScopeImpl<>(executorService, completionService, futures, (ExceptionHandler<Exception, ? extends R, ? extends F>) handler, deadline);
  }

  @Override
  public <T> T result(Function<? super Stream<R>, T> streamMapper) throws E, DeadlineException, InterruptedException {
    if (executorService.isShutdown()) {
      throw new IllegalStateException("result already called");
    }
    executorService.shutdown();

    var spliterator = completionService == null?
        orderedSpliterator(executorService, futures, handler, deadline):
        unorderedSpliterator(executorService, completionService, futures, handler, deadline);
    var stream = StreamSupport.stream(spliterator, false);
    return streamMapper.apply(stream);
  }

  @Override
  public void close() {
    if (!executorService.isTerminated()) {
      executorService.shutdownNow();
      try {
        while(!executorService.awaitTermination(1, TimeUnit.DAYS)) {
          // empty
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }


  // Utilities
  // this code is duplicated in AsyncMonadImpl

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
          } catch (TimeoutException e) {
            throw new DeadlineException("deadline reached", e);
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
            throw new DeadlineException("deadline reached");
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
