package fr.umlv.loom.example;

import jdk.incubator.concurrent.StructuredTaskScope;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

// $JAVA_HOME/bin/java --enable-preview --add-modules jdk.incubator.concurrent -cp target/loom-1.0-SNAPSHOT.jar  fr.umlv.loom.example._14_ignore_error
public interface _14_adhoc_scope {
  class FutureTaskImpl<T> extends FutureTask<T> {
    public FutureTaskImpl(Callable<T> callable) {
      super(callable);
    }

    @Override
    public void set(T o) {
      super.set(o);
    }
  }

  class IgnoreErrorStructuredTaskScope<T> extends StructuredTaskScope<T> {
    private final T defaultValue;
    private final ConcurrentHashMap<Future<?>, FutureTaskImpl<?>> map = new ConcurrentHashMap<>();

    public IgnoreErrorStructuredTaskScope(T defaultValue) {
      this.defaultValue = defaultValue;
    }

    @Override
    public <U extends T> Future<U> fork(Callable<? extends U> task) {
      var future = super.fork(task);
      var impl = new FutureTaskImpl<>(task);
      map.put(future, impl);
      return (Future<U>) impl;
    }

    @Override
    protected void handleComplete(Future<T> future) {
      var impl = (FutureTaskImpl<T>) map.remove(future);
      switch (future.state()) {
        case SUCCESS -> impl.set(future.resultNow());
        case FAILED -> impl.set(defaultValue);
        case CANCELLED -> impl.cancel(true);
        case RUNNING -> throw new AssertionError();
      }
    }
  }

  static void main(String[] args) throws InterruptedException {
    try(var scope = new IgnoreErrorStructuredTaskScope<>(0)) {
      var future1 = scope.fork(() -> {
        Thread.sleep(50);
        return 1;
      });
      var future2 = scope.fork(() -> {
        Thread.sleep(50);
        throw new AssertionError("oops");
      });
      scope.join();
      System.out.println(future1.resultNow() + future2.resultNow());
    }
  }
}
