package fr.umlv.loom.structured;

import java.lang.reflect.Modifier;
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

record AsyncScopeImpl<R, E extends Exception>(
    ExecutorService executorService,
    ExecutorCompletionService<R> completionService,
    ArrayList<Future<R>> futures,
    AsyncScope.ExceptionHandler<Exception, ? extends R, ? extends E> handler,
    Instant deadline
    ) implements AsyncScope<R, E> {

  private static final boolean ASSERTION_ENABLED;
  static {
    var assertionEnabled = false;
    //noinspection AssertWithSideEffects
    assert assertionEnabled = true;
    ASSERTION_ENABLED = assertionEnabled;
  }

  private static final class Debug {
    private static final ClassValue<String> CLASS_VALUE = new ClassValue<>() {
      @Override
      protected String computeValue(Class<?> type) {
        return isClassAllowed(type);
      }
    };

    private static String isClassAllowed(Class<?> type) {
      if (!Modifier.isFinal(type.getModifiers())) {
        return type.getName() + " is not final";
      }
      for(Class<?> t = type; t != Object.class; t = t.getSuperclass()) {
        for (var field : t.getDeclaredFields()) {
          var modifiers = field.getModifiers();
          if (Modifier.isStatic(modifiers) || Modifier.isVolatile(modifiers)) {
            continue;
          }
          if (!Modifier.isFinal(modifiers)) {
            return field + " is not declared final or volatile";
          }
          var result = isTypeAllowed(field.getType());
          if (result != null) {
            return "for field " + field + ", " + result;
          }
        }
      }
      return null;
    }

    private static String isTypeAllowed(Class<?> type) {
      if (type.isRecord()) {
        for(var component: type.getRecordComponents()) {
          var result = isTypeAllowed(component.getType());
          if (result != null) {
            return "for component " + component + ", " + result;
          }
        }
        return null;
      }
      if (type.isPrimitive()) {
        return null;
      }
      var packageName = type.getPackageName();
      if (packageName.equals("java.time")
          || packageName.equals("java.util.concurrent")
          || packageName.equals("java.util.concurrent.atomic")
          || packageName.equals("java.util.concurrent.locks")) {
        return null;
      }
      return switch (type.getName()) {
        case "java.lang.String",
            "java.lang.Void", "java.lang.Boolean", "java.lang.Byte", "java.lang.Character", "java.lang.Short",
            "java.lang.Integer", "java.lang.Long", "java.lang.Float", "java.lang.Double",
            "java.util.Random" -> null;
        default -> CLASS_VALUE.get(type);
      };
    }

    private static void checkAsyncTaskUseOnlyImmutableData(Class<?> clazz) {
      var result = CLASS_VALUE.get(clazz);
      if (result != null) {
        throw new IllegalStateException("lambda " + clazz.getName()+ " captures modifiable state " + result);
      }
    }
  }

  public static <R, E extends Exception> AsyncScope<R,E> of(boolean ordered) {
    var executorService = Executors.newVirtualThreadPerTaskExecutor();
    var completionService = ordered? null: new ExecutorCompletionService<R>(executorService);
    return new AsyncScopeImpl<>(executorService, completionService, new ArrayList<>(), null, null);
  }

  @Override
  public AsyncScope<R, E> async(Task<? extends R, ? extends E> task) {
    if (executorService.isShutdown()) {
      throw new IllegalStateException("result already called");
    }
    if (ASSERTION_ENABLED) {
      Debug.checkAsyncTaskUseOnlyImmutableData(task.getClass());
    }
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
  public AsyncScope<R, E> deadline(Instant deadline) {
    Objects.requireNonNull(deadline);
    if (executorService.isShutdown()) {
      throw new IllegalStateException("result already called");
    }
    if (this.deadline != null) {
      throw new IllegalStateException("deadline already set");
    }
    return new AsyncScopeImpl<>(executorService, completionService, futures, handler, deadline);
  }

  @Override
  public <T> T await(Function<? super Stream<R>, ? extends T> streamMapper) throws E, DeadlineException, InterruptedException {
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
