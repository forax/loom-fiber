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
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

record AsyncMonadImpl<R, E extends Exception>(
    ExecutorService executorService,
    ExecutorCompletionService<R> completionService,
    List<Future<R>> futures,
    boolean ordered,
    ExceptionHandler<Exception, ? extends R, ? extends E> handler,
    Instant deadline) implements AsyncMonad<R,E> {

  public static <R, E extends Exception> AsyncMonadImpl<R, E> of(Consumer<? super TaskForker<R, E>> taskForkerConsumer) {
    Objects.requireNonNull(taskForkerConsumer, "taskForkerConsumer is null");
    var executorService = Executors.newVirtualThreadPerTaskExecutor();
    var completionService = new ExecutorCompletionService<R>(executorService);
    var futures = new ArrayList<Future<R>>();
    taskForkerConsumer.accept((TaskForker<R, E>) task -> {
      Objects.requireNonNull(task, "task is null");
      futures.add(completionService.submit(task::compute));
    });
    executorService.shutdown();
    return new AsyncMonadImpl<>(executorService, completionService, futures, true, null, null);
  }

  @Override
  public AsyncMonad<R, E> unordered() {
    return new AsyncMonadImpl<>(executorService, completionService, futures,false, handler, deadline);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <F extends Exception> AsyncMonad<R, F> recover(ExceptionHandler<? super E, ? extends R, ? extends F> handler) {
    Objects.requireNonNull(handler, "handler is null");
    if (this.handler != null) {
      throw new IllegalStateException("an exception handler is already set");
    }
    return new AsyncMonadImpl<>(executorService, completionService, futures, ordered, (ExceptionHandler<Exception, ? extends R, ? extends F>) handler, deadline);
  }

  @Override
  public AsyncMonad<R, E> deadline(Instant deadline) {
    Objects.requireNonNull(deadline, "deadline is null");
    if (this.deadline != null) {
      throw new IllegalStateException("a deadline is already set");
    }
    return new AsyncMonadImpl<>(executorService, completionService, futures, ordered, handler, deadline);
  }

  private long deadlineAsTimeoutInNanos() {
    var timeout = Duration.between(Instant.now(), deadline).toNanos();
    return timeout < 0? 0: timeout;
  }

  @Override
  public <T> T result(Function<? super Stream<R>, T> streamMapper) throws E, DeadlineException, InterruptedException {
    Objects.requireNonNull(streamMapper, "streamMapper is null");
    Spliterator<R> spliterator;
    if (ordered) {
      spliterator = new Spliterator<>() {
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
                  future.get(deadlineAsTimeoutInNanos(), TimeUnit.NANOSECONDS);
            } catch (ExecutionException e) {
              result = handleException(e.getCause());
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
    } else {
      spliterator = new Spliterator<>() {
        private int index;

        @Override
        public boolean tryAdvance(Consumer<? super R> action) {
          if (index == futures.size()) {
            return false;
          }
          try {
            var future = deadline == null?
                completionService.take():
                completionService.poll(deadlineAsTimeoutInNanos(), TimeUnit.NANOSECONDS);
            if (future == null) {
              throw new DeadlineException("deadline reached");
            }
            R result;
            try {
              result = future.get();
            } catch (ExecutionException e) {
              result = handleException(e.getCause());
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
    var stream = StreamSupport.stream(spliterator, false);
    return streamMapper.apply(stream);
  }

  private R handleException(Throwable cause) throws InterruptedException {
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
  static <T extends Throwable> AssertionError rethrow(Throwable cause) throws T {
    throw (T) cause;
  }

  @Override
  public void close() {
    if (!executorService.isTerminated()) {
      executorService.shutdownNow();
    }
  }
}
