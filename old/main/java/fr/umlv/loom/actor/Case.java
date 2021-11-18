package fr.umlv.loom.actor;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class Case {
  private final Class<?> type;
  private final BiConsumer<Object, Actor> consumer;
  
  private Case(Class<?> type, BiConsumer<Object, Actor> consumer) {
    this.type = type;
    this.consumer = consumer;
  }

  static <T> Case Case(Class<T> type, Consumer<? super T> consumer) {
    return new Case(type, (content, sender) -> consumer.accept(type.cast(content)));
  }
  
  static <T> Case Case(Class<T> type, BiConsumer<? super T, Object> consumer) {
    return new Case(type, (content, sender) -> consumer.accept(type.cast(content), sender));
  }
  
  static void call(Object content, Actor sender, Case... cases) {
    for(var aCase: cases) {
      if (aCase.type.isInstance(content)) {
        aCase.consumer.accept(content, sender);
        return;
      }
    }
    throw new IllegalStateException("invalid message " + content + " from " + sender);
  }
}