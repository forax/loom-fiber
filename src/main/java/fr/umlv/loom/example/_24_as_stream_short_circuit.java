package fr.umlv.loom.example;

import fr.umlv.loom.structured.StructuredScopeAsStream;
import fr.umlv.loom.structured.StructuredScopeAsStream.Result;

import java.io.IOException;
import java.util.stream.Collector;
import java.util.stream.Collectors;

// $JAVA_HOME/bin/java --enable-preview -cp target/classes  fr.umlv.loom.example._24_as_stream_short_circuit
// docker run -it --rm --user forax -v /Users/forax:/home/forax -w /home/forax/git/loom-fiber fedora $JAVA_HOME/bin/java --enable-preview -cp target/classes fr.umlv.loom.example._24_as_stream_short_circuit
public interface _24_as_stream_short_circuit {
  static void main(String[] args) throws InterruptedException {
    try (var scope = new StructuredScopeAsStream<Integer, IOException>()) {
      var task1 = scope.fork(() -> {
        Thread.sleep(1_000);
        return 1_000;
      });
      var task2 = scope.fork(() -> {
        Thread.sleep(42);
        //throw new IOException();
        return 42;
      });
      var optional = scope.joinAll(s -> s.flatMap(Result::keepOnlySuccess).findFirst());
      System.out.println(optional);
    }
  }
}
