package fr.umlv.loom.reducer;

import fr.umlv.loom.reducer.StructuredAsyncScope.Reducer;
import fr.umlv.loom.reducer.StructuredAsyncScope.Result;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

// $JAVA_HOME/bin/java --enable-preview --add-modules jdk.incubator.concurrent ...
public class StructAsyncScopeDemo {
  public static void toList() throws InterruptedException {
    try(var scope = StructuredAsyncScope.of(Reducer.<Integer>toList())) {
      scope.fork(() -> 3);
      scope.fork(() -> {
        throw new IOException();
      });

      List<Result<Integer>> list = scope.result();
      System.out.println(list);  // [Result[state=FAILED, element=null, suppressed=java.io.IOException], Result[state=SUCCEED, element=3, suppressed=null]]
    }
  }

  public static void max() throws InterruptedException {
    try(var scope = StructuredAsyncScope.of(Reducer.max(Integer::compareTo))) {
      scope.fork(() -> 3);
      scope.fork(() -> {
        throw new IOException();
      });
      scope.fork(() -> 42);

      Optional<Result<Integer>> max = scope.result();
      System.out.println(max);  // Optional[Result[state=SUCCEED, element=42, suppressed=java.io.IOException]]
    }
  }

  public static void first() throws InterruptedException {
    try(var scope = StructuredAsyncScope.of(Reducer.<Integer>first())) {
      scope.fork(() -> {
        throw new IOException();
      });
      scope.fork(() -> 3);
      scope.fork(() -> 42);

      Optional<Result<Integer>> first = scope.result();
      System.out.println(first);  // Optional[Result[state=SUCCEED, element=3, suppressed=java.io.IOException]]
    }
  }


  public static void main(String[] args) throws InterruptedException {
    toList();
    max();
    first();
  }
}
