package fr.umlv.loom.monad;

import java.util.Collection;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

public interface AsyncMonad<R, E extends Exception> extends AutoCloseable {
  interface Task<R, E extends Exception> {
    R compute() throws E, InterruptedException;
  }

  interface TaskForker<R, E extends Exception> {
    void fork(Task<R, E> task);
  }

  static <R, E extends Exception> AsyncMonad<R,E> of(Consumer<? super TaskForker<R, E>> taskForkerConsumer) {
    return AsyncMonadImpl.of(taskForkerConsumer);
  }

  static <R, E extends Exception> AsyncMonad<R,E> of(Collection<? extends Task<R, E>> tasks) {
    var asyncMonad = AsyncMonad.<R, E>of(consumer -> {
      for (var task : tasks) {
        consumer.fork(task);
      }
    });
    var isOrdered = tasks.spliterator().hasCharacteristics(Spliterator.ORDERED);
    return isOrdered? asyncMonad: asyncMonad.unordered();
  }

  AsyncMonad<R,E> unordered();

  interface ExceptionHandler<E extends Exception, R, F extends Exception> {
    R handle(E exception) throws F;
  }

  <F extends Exception> AsyncMonad<R,F> recover(ExceptionHandler<? super E, ? extends R, ? extends F> handler);

  <T> T result(Function<? super Stream<R>, T> streamMapper) throws E, InterruptedException;

  @Override
  void close();
}
