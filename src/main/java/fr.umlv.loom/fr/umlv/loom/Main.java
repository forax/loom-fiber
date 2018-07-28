package fr.umlv.loom;

import static java.util.stream.IntStream.range;

public class Main {
  public static void main(String[] args) {
    var it = Generator.iterator(consumer -> {
      range(0, 10).forEach(consumer::accept);
    }); 

    it.forEachRemaining(System.out::println);
  }
}
