package fr.umlv.loom.example;

import fr.umlv.loom.structured.StructuredScopeAsStream;
import fr.umlv.loom.structured.StructuredScopeAsStream.Result;

import java.io.IOException;
import java.util.stream.Collectors;

// $JAVA_HOME/bin/java --enable-preview -cp target/classes  fr.umlv.loom.example._23_as_stream
public interface _23_as_stream {
  static void main(String[] args) throws /*IOException,*/ InterruptedException {
    try (var scope = new StructuredScopeAsStream<Integer, IOException>()) {
      scope.fork(() -> {
        Thread.sleep(1_000);
        return 1;
      });
      scope.fork(() -> {
        Thread.sleep(42);
        throw new IOException();
        //return 2;
      });
      //var list = scope.joinAll(stream -> stream.toList());
      //System.out.println(list);

      var result = scope.joinAll(s -> s.collect(Result.toResult(Collectors.summingInt(v -> v))));
      //System.out.println(result.get());
    }
  }
}
