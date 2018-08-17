package fr.umlv.loom;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public class Server {
  static final ContinuationScope SCOPE = new ContinuationScope("server");
  
  public interface IO {
    int read(ByteBuffer buffer) throws IOException;
    int write(ByteBuffer buffer) throws IOException;
    
    default String readLine(ByteBuffer buffer, Charset charset) throws IOException {
      int read;
      loop: while((read = read(buffer)) != -1) {
        buffer.flip();
        while(buffer.hasRemaining()) {
          if(buffer.get() == '\n') {
            break loop;
          }
        }
        if (buffer.position() == buffer.capacity()) {
          throw new IOException("string too big");
        }
        buffer.limit(buffer.capacity());
      }
      if (read == -1) {
        return null;
      }
      buffer.flip();
      byte[] array = new byte[buffer.limit() - 2];
      buffer.get(array);
      buffer.get(); // skip '\n'
      return new String(array, charset);
    }
    
    default void write(String text, Charset charset) throws IOException {
      write(ByteBuffer.wrap(text.getBytes(charset)));
    }
  }
  
  public interface IOConsumer {
    void accept(ByteBuffer buffer, IO io) throws IOException;
  }
  
  
  static void closeUnconditionnaly(Closeable closeable) {
    try {
      closeable.close();
    } catch (IOException e) {
      // do nothing
    }
  }
  
  private static void mainLoop(IOConsumer consumer, int localPort) throws IOException {
    var server = ServerSocketChannel.open();
    server.configureBlocking(false);
    server.bind(new InetSocketAddress(localPort));
    var selector = Selector.open();
    var acceptContinuation = new Continuation(SCOPE, () -> {
      for(;;) {
        SocketChannel channel;
        try {
          channel = server.accept();
        } catch (IOException e) {
          System.err.println(e.getMessage());
          closeUnconditionnaly(server);
          closeUnconditionnaly(selector);
          return;
        }
        SelectionKey key;
        try {
          channel.configureBlocking(false);
          key = channel.register(selector, 0);
        } catch (IOException e) {
          System.err.println(e.getMessage());
          closeUnconditionnaly(channel);
          return;
        }
        
        var continuation = new Continuation(SCOPE, () -> {
          var buffer = ByteBuffer.allocateDirect(8192);
          var io = new IO() {
            @Override
            public int read(ByteBuffer buffer) throws IOException {
              int read;
              while ((read = channel.read(buffer)) == 0) {
                key.interestOps(SelectionKey.OP_READ);
                Continuation.yield(SCOPE);
              }
              key.interestOps(0);
              return read;
            }

            @Override
            public int write(ByteBuffer buffer) throws IOException {
              int written;
              while ((written = channel.write(buffer)) == 0) {
                key.interestOps(SelectionKey.OP_WRITE);
                Continuation.yield(SCOPE);
              }
              key.interestOps(0);
              return written;
            }
          };
          try {
            consumer.accept(buffer, io);
          } catch (IOException|RuntimeException e) {
            e.printStackTrace();
          } finally {
            key.cancel();
            closeUnconditionnaly(channel);
          }
        });
        key.attach(continuation);
        continuation.run();
        Continuation.yield(SCOPE);
      }
    });
    server.register(selector, SelectionKey.OP_ACCEPT, acceptContinuation);
    for(;;) {
      selector.select(key -> ((Continuation)key.attachment()).run());
    }
  }
  
  public static void main(String[] args) throws IOException {
    System.out.println("start server on local port 7000");
    /*
    mainLoop((buffer, io) -> {
      while(io.read(buffer) != -1) {
        buffer.flip();
        io.write(buffer);
        buffer.clear();
      }
    }, 7000);
    */
    
    mainLoop((buffer, io) -> {
      String line = io.readLine(buffer, ISO_8859_1);
      //System.err.println("request " + line);
      if (line == null) {
        return;
      }
      
      Path path = Path.of(".").resolve(line.split(" ")[1].substring(1));
      //System.err.println(path);
      
      byte[] data;
      try {
        data = Files.readAllBytes(path);
      } catch(NoSuchFileException e) {
        io.write("HTTP/0.9 404 Not Found\n\n", ISO_8859_1);
        return;
      }
      
      io.write("HTTP/0.9 200 OK\nContent-Length:" + data.length +"\n\n", ISO_8859_1);
      io.write(ByteBuffer.wrap(data));
      
    }, 7000);
  }
}
