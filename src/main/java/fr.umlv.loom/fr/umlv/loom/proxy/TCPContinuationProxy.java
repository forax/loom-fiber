package fr.umlv.loom.proxy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;

public class TCPContinuationProxy {
  private static final ContinuationScope SCOPE = new ContinuationScope("scope");
  
  private static Runnable runnable(Scheduler scheduler, SocketChannel socket1, SocketChannel socket2) {
    return () -> {
      var buffer = ByteBuffer.allocate(8192);
      
      System.out.println("start continuation " + Continuation.getCurrentContinuation(SCOPE));
      try(socket1; socket2) {
        for(;;) {
          int read;
          while ((read = socket1.read(buffer)) == 0) {
            scheduler.register(socket1, SelectionKey.OP_READ);
            System.out.println("yield read from " + Continuation.getCurrentContinuation(SCOPE));
            Continuation.yield(SCOPE);
          }
          System.out.println("read " + read + " from " +Continuation.getCurrentContinuation(SCOPE));
          if (read == -1) {
            socket1.close();
            socket2.close();
            return;
          }
          buffer.flip();

          do {
            while ((socket2.write(buffer)) == 0) {
              scheduler.register(socket2, SelectionKey.OP_WRITE);
              System.out.println("yield write from " + Continuation.getCurrentContinuation(SCOPE));
              Continuation.yield(SCOPE);
            }
            
            System.out.println("write from " +Continuation.getCurrentContinuation(SCOPE));
            
          } while(buffer.hasRemaining());
          
          buffer.clear();
        }
      } catch (@SuppressWarnings("unused") IOException e) {
        //throw new UncheckedIOException(e);
      }
    };
  }
  
  
  static class Scheduler {
    private final Selector selector;
    private final ArrayDeque<Continuation> injected = new ArrayDeque<>();
    
    public Scheduler(Selector selector) {
      this.selector = selector;
    }

    public void inject(Continuation continuation) {
      injected.add(continuation);
    }
    
    public void register(SocketChannel channel, int operation) throws ClosedChannelException {
      channel.register(selector, operation, Continuation.getCurrentContinuation(SCOPE));
    }
    
    public void loop() throws IOException {
      for(;;) {
        while(!injected.isEmpty()) {
          var continuation = injected.poll();
          System.out.println("injected continuation " + continuation + " run");
          continuation.run();
        }
        selector.select(key -> {
          var continuation = (Continuation)key.attachment();
          key.interestOps(0);
          System.out.println("continuation " + continuation + " run");
          continuation.run();
        });
        if (selector.keys().stream().noneMatch(SelectionKey::isValid)) {
          return;
        }
      }
    }
  }
  
  @SuppressWarnings("resource")
  public static void main(String[] args) throws IOException {
    var server = ServerSocketChannel.open();
    server.bind(new InetSocketAddress(7777));
    System.out.println("server bound to " + server.getLocalAddress());
    
    var remote = SocketChannel.open();
    remote.connect(new InetSocketAddress(InetAddress.getByName(Host.NAME), 7));
    remote.configureBlocking(false);
    
    var selector = Selector.open();
    var scheduler = new Scheduler(selector);
    
    System.out.println("accepting ...");
    var client = server.accept();
    client.configureBlocking(false);
    
    var cont1 = new Continuation(SCOPE, runnable(scheduler, client, remote));
    var cont2 = new Continuation(SCOPE, runnable(scheduler, remote, client));
    scheduler.inject(cont1);
    scheduler.inject(cont2);
    scheduler.loop();
  }
}
