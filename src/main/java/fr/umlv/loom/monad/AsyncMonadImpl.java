package fr.umlv.loom.monad;

import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

record AsyncMonadImpl<R, E extends Exception>(
    ExecutorService executorService,
    ExecutorCompletionService<R> completionService,
    List<Future<R>> futures,
    boolean ordered,
    ExceptionHandler<Exception, ? extends R, ? extends E> handler) implements AsyncMonad<R,E> {
  public static <R, E extends Exception> AsyncMonadImpl<R, E> of(Consumer<? super TaskForker<R, E>> taskForkerConsumer) {
    var executorService = Executors.newVirtualThreadPerTaskExecutor();
    var completionService = new ExecutorCompletionService<R>(executorService);
    var futures = new ArrayList<Future<R>>();
    taskForkerConsumer.accept((TaskForker<R, E>) task -> {
      futures.add(completionService.submit(task::compute));
    });
    executorService.shutdown();
    return new AsyncMonadImpl<>(executorService, completionService, futures, true, null);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <F extends Exception> AsyncMonad<R, F> recover(ExceptionHandler<? super E, ? extends R, ? extends F> handler) {
    if (this.handler != null) {
      throw new IllegalStateException("an exception handler is already specified");
    }
    return new AsyncMonadImpl<>(executorService, completionService, futures, ordered, (ExceptionHandler<Exception, ? extends R, ? extends F>) handler);
  }

  @Override
  public AsyncMonad<R, E> unordered() {
    return new AsyncMonadImpl<>(executorService, completionService, futures,false, handler);
  }

  @Override
  public <T> T result(Function<? super Stream<R>, T> streamMapper) throws E, InterruptedException {
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
            try {
              action.accept(future.get());
            } catch (ExecutionException e) {
              action.accept(handleException(e.getCause()));
            }
          } catch (InterruptedException e) {
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
      spliterator = new Spliterator<R>() {
        private int index;

        @Override
        public boolean tryAdvance(Consumer<? super R> action) {
          if (index == futures.size()) {
            return false;
          }
          try {
            var future = completionService.take();
            try {
              action.accept(future.get());
            } catch (ExecutionException e) {
              action.accept(handleException(e.getCause()));
            }
            index++;
            return true;
          } catch (InterruptedException e) {
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
