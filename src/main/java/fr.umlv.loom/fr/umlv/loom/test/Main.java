package fr.umlv.loom.test;

import static fr.umlv.loom.test.JayTest.*;

public class Main {
  public static void main(String[] args) {
    // typesafe API loosely based on JavaScript library Jest

    test("all", () -> {

      test("two plus two is four", () -> {
        //expect(2 + 2).toBe(4);
        expect(2 + 2).toBe(3);
      });

      test("two plus two is not three", () -> {
        //expect(2 + 2).not().toBe(3);
        expect(2 + 2).not().toBe(4);
      });

      test("return value is correct", () -> {
        expect(() -> "hell").returnValue().toBe("hello");
        expect(() -> "ban").returnValue().toBe("banzai");
      });

    });
  }
}
