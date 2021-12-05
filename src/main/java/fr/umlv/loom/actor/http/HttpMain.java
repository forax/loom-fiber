package fr.umlv.loom.actor.http;

import fr.umlv.loom.actor.Actor;
import fr.umlv.loom.actor.Actor.Context;
import fr.umlv.loom.actor.http.HttpServer.PathVariable;
import fr.umlv.loom.actor.http.HttpServer.RequestBody;
import fr.umlv.loom.actor.http.HttpServer.RequestMapping;
import fr.umlv.loom.actor.http.HttpServer.Response;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import static fr.umlv.loom.actor.http.HttpServer.HttpStatus.NOT_FOUND;
import static fr.umlv.loom.actor.http.HttpServer.HttpStatus.OK;
import static fr.umlv.loom.actor.http.HttpServer.RequestMethod.DELETE;
import static fr.umlv.loom.actor.http.HttpServer.RequestMethod.POST;

public class HttpMain {
  record Task(long id, String content) {}
  record TaskWithoutId(String content) {}

  record TaskController(Context context, List<Task> tasks) {
    @RequestMapping(path = "/tasks")
    public void getAllTasks(Actor<Response<List<Task>>> reply) {
      System.err.println("getAllTasks");
      context.postTo(reply, $ -> $.response(OK, tasks));
    }

    @RequestMapping(path = "/tasks", method = POST)
    public void createTask(@RequestBody TaskWithoutId taskWithoutId, Actor<Response<Task>> reply) {
      System.err.println("createTask " + taskWithoutId);
      var task = new Task(tasks.size(), taskWithoutId.content);
      tasks.add(task);
      context.postTo(reply, $ -> $.response(OK, task));
    }

    @RequestMapping(path = "/tasks/{id}", method = DELETE)
    public void deleteTask(@PathVariable("id") String id, Actor<Response<Void>> reply) {
      System.err.println("deleteTask " + id);
      var taskId = Integer.parseInt(id);
      var removed = tasks.removeIf(task -> task.id == taskId);
      if (!removed) {
        context.postTo(reply, $ -> $.response(NOT_FOUND, null));
        return;
      }
      context.postTo(reply, $ -> $.response(OK, null));
    }
  }

  private static TaskController taskController(Context context) {
    var tasks = new ArrayList<Task>();
    // initial task
    tasks.add(new Task(0, "Hello from an http server powered by loom !"));
    return new TaskController(context, tasks);
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    var actor = Actor.of(TaskController.class)
        .behavior(HttpMain::taskController)
        .onSignal((signal, context) -> {
          context.restart();  // restart if an error occurs
        });

    new HttpServer()
        .routes(actor)
        .bind(new InetSocketAddress("localhost", 8080));
  }
}
