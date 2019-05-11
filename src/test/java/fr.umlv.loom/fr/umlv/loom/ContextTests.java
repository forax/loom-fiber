package fr.umlv.loom;

import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

@SuppressWarnings("static-method")
class ContextTests {
  @Test
  void defaultValue() {
    var context = new Context<>(() -> 42);
    context.enter(() -> {
      assertEquals(42, (int)context.getValue());
    });
  }
  
  @Test
  void enclosed() {
    var context = new Context<>(() -> 1);
    var context2 = new Context<>(() -> 2);
    
    context.enter(() -> {
      context2.enter(() -> {
        assertEquals(1, (int)context.getValue());
        assertEquals(2, (int)context2.getValue());
      });
    });
  }
  
  @Test
  void multipleThreads() throws InterruptedException {
    var context = new Context<Thread>(() -> null);
    
    var threads = range(0, 4).mapToObj(i -> {
      return new Thread(() -> {
        context.enter(() -> {
          context.setValue(Thread.currentThread());
          assertEquals(Thread.currentThread(), context.getValue());
        });
      });
    }).collect(toList());
    
    threads.forEach(Thread::start);
    
    for(var thread: threads) {
      thread.join();
    }
  }
}
