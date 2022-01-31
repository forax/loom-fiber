package fr.umlv.loom.proxy;

import fr.umlv.loom.executor.UnsafeExecutors;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executors;

public class TCPVirtualThreadProxy {
  private static Runnable runnable(SocketChannel socket1, SocketChannel socket2) {
    return () -> {
      var buffer = ByteBuffer.allocate(8192);
      
      System.out.println("start " + Thread.currentThread());
      try(socket1; socket2) {
        for(;;) {
          int read = socket1.read(buffer);
          System.out.println("read " + read + " from " + Thread.currentThread());
          if (read == -1) {
            //socket1.close();
            //socket2.close();
            return;
          }
          buffer.flip();

          do {
            socket2.write(buffer);
            System.out.println("write from " + Thread.currentThread());
            
          } while(buffer.hasRemaining());
          
          buffer.clear();
        }
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    };
  }
  
  @SuppressWarnings("resource")
  public static void main(String[] args) throws IOException, InterruptedException {
    var server = ServerSocketChannel.open();
    server.bind(new InetSocketAddress(7777));
    System.out.println("server bound to " + server.getLocalAddress());
    
    var remote = SocketChannel.open();
    remote.connect(new InetSocketAddress(InetAddress.getByName(Host.NAME), Host.PORT));
    //remote.configureBlocking(false);
    
    System.out.println("accepting ...");
    var client = server.accept();
    //client.configureBlocking(false);

    var executor = UnsafeExecutors.virtualThreadExecutor(Executors.newSingleThreadExecutor());
    executor.execute(runnable(client, remote));
    executor.execute(runnable(remote, client));
  }
}
