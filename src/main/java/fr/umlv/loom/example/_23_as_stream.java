package fr.umlv.loom.example;

import fr.umlv.loom.structured.StructuredScopeAsStream;

import java.io.IOException;
import java.util.stream.Collectors;

// $JAVA_HOME/bin/java --enable-preview -cp target/classes  fr.umlv.loom.example._23_as_stream
// docker run -it --rm --user forax -v /Users/forax:/home/forax -w /home/forax/git/loom-fiber fedora $JAVA_HOME/bin/java --enable-preview -cp target/classes fr.umlv.loom.example._23_as_stream
public interface _23_as_stream {
  static void main(String[] args) throws InterruptedException {
    try (var scope = new StructuredScopeAsStream<Integer, IOException>()) {
      var task1 = scope.fork(() -> {
        Thread.sleep(1_000);
        return 1;
      });
      var task2 = scope.fork(() -> {
        Thread.sleep(42);
        throw new IOException();
        //return 2;
      });
      var list = scope.joinAll(stream -> stream.toList());
      System.out.println(list);

      //var result = scope.joinAll(s -> s.collect(StructuredScopeAsStream.Result.toResult(Collectors.toList())));
      //System.out.println(result);
    }
  }
}
