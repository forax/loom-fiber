package fr.umlv.loom;

import static java.util.stream.IntStream.range;

public class Main {
  public static void main(String[] args) {
    var it = Generators.iterator(consumer -> {
      range(0, 5).forEach(consumer::accept);
    }); 
    it.forEachRemaining(System.out::println);
    
    Generators.stream(consumer -> {
      range(0, 5).forEach(consumer::accept);
    }).forEach(System.out::println);
  }
}
