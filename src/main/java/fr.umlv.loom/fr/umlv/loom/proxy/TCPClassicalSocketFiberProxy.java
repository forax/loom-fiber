package fr.umlv.loom.proxy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executors;

public class TCPClassicalSocketFiberProxy {
  private static Runnable runnable(Socket socket1, Socket socket2) {
    return () -> {
      var buffer = new byte[8192];
      
      System.out.println("start " + Fiber.current().orElseThrow());
      try(socket1;
          socket2;
          var input1 = socket1.getInputStream();
          var output2 = socket2.getOutputStream();) {
        for(;;) {
          int read = input1.read(buffer);
          System.out.println("read " + read + " from " + Fiber.current().orElseThrow());
          if (read == -1) {
            input1.close();
            output2.close();
            socket1.close();
            socket2.close();
            return;
          }

          output2.write(buffer, 0, read);
          System.out.println("write from " + Fiber.current().orElseThrow());
        }
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    };
  }
  
  public static void main(String[] args) throws IOException {
    var server = new ServerSocket();
    server.bind(new InetSocketAddress(7777));
    System.out.println("server bound to " + server.getLocalSocketAddress());
    
    var remote = new Socket();
    remote.connect(new InetSocketAddress(InetAddress.getByName(Host.NAME), 7));
    //remote.configureBlocking(false);
    
    System.out.println("accepting ...");
    var client = server.accept();
    //client.configureBlocking(false);
    
    var executor = Executors.newSingleThreadExecutor();
    //var executor = ForkJoinPool.commonPool();
    Fiber.schedule(executor, runnable(client, remote));
    Fiber.schedule(executor, runnable(remote, client));
  }
}
