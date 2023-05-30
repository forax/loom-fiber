package fr.umlv.loom.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

// A HTTP server that serve static files and answer to 3 services
//   GET /tasks returns a list of tasks as a json object
//   POST /tasks take the content as a JSON text and add it as a new task
//   DELETE /tasks/id delete a task by its id

// $JAVA_HOME/bin/java --enable-preview -cp target/loom-1.0-SNAPSHOT.jar  fr.umlv.loom.example._14_http_server
public interface _14_http_server {
  record Task(int id, String content) {
    public String toJSON() {
      return """
          {
            "id": %d,
            "content": "%s"
          }
          """.formatted(id, content);
    }
  }

  private static void getTasks(HttpExchange exchange, List<Task> tasks) throws IOException {
    System.err.println("thread " + Thread.currentThread());
    var uri = exchange.getRequestURI();
    System.out.println("GET query " + uri);

    try(exchange) {
      var json = tasks.stream()
          .map(Task::toJSON)
          .collect(joining(", ", "[", "]"));
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, json.length());
      try (var writer = new OutputStreamWriter(exchange.getResponseBody(), UTF_8)) {
        writer.write(json);
      }
    }
  }

  Pattern REGEX = Pattern.compile("""
       [^\\:]+:"(.*)"\
       """);

  private static void postTasks(HttpExchange exchange, List<Task> tasks) throws IOException {
    System.err.println("thread " + Thread.currentThread());
    var uri = exchange.getRequestURI();
    System.out.println("POST query " + uri);

    try (exchange) {
      String content;
      try (var input = new InputStreamReader(exchange.getRequestBody(), UTF_8);
           var reader = new BufferedReader(input)) {
        var line = reader.readLine();
        var matcher = REGEX.matcher(line);
        matcher.find();
        content = matcher.group(1);
      }
      System.out.println("content " + content);
      var task = new Task(tasks.size(), content);
      tasks.add(task);
      var json = task.toJSON();
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, json.length());
      try (var writer = new OutputStreamWriter(exchange.getResponseBody(), UTF_8)) {
        writer.write(json);
      }
    }
  }

  private static void deleteTasks(HttpExchange exchange, List<Task> tasks) throws IOException {
    System.err.println("thread " + Thread.currentThread());
    var uri = exchange.getRequestURI();
    System.out.println("DELETE query " + uri);

    try (exchange) {
      var path = Path.of(uri.toString());
      var id = Integer.parseInt(path.getFileName().toString());
      System.out.println("id " + id);
      tasks.removeIf(task -> task.id == id);
      exchange.sendResponseHeaders(200, 0);
    }
  }

  private static void getStaticContent(HttpExchange exchange) throws IOException {
    System.err.println("thread " + Thread.currentThread());
    var uri = exchange.getRequestURI();
    var path = Path.of(".", uri.toString());
    System.out.println("GET query " + uri + " to " + path);

    try(exchange) {
      exchange.sendResponseHeaders(200, Files.size(path));
      Files.copy(path, exchange.getResponseBody());
    } catch(IOException e) {
      exchange.sendResponseHeaders(404, 0);
    }
  }

  static void main(String[] args) throws IOException {
    var executor = Executors.newVirtualThreadPerTaskExecutor();
    var localAddress = new InetSocketAddress(8080);
    System.out.println("server at http://localhost:" + localAddress.getPort() + "/todo.html");

    var tasks = new CopyOnWriteArrayList<Task>();

    var server = HttpServer.create();
    server.setExecutor(executor);
    server.bind(localAddress, 0);
    server.createContext("/", exchange -> getStaticContent(exchange));
    server.createContext("/tasks", exchange -> {
      switch (exchange.getRequestMethod()) {
        case "GET" -> getTasks(exchange, tasks);
        case "POST" -> postTasks(exchange, tasks);
        case "DELETE" -> deleteTasks(exchange, tasks);
        default -> throw new IllegalStateException("unknown");
      }
    });
    server.start();
  }
}
