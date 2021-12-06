package fr.umlv.loom.actor;

import fr.umlv.loom.actor.Actor.Context;
import fr.umlv.loom.actor.Actor.ShutdownSignal;

import java.util.ArrayList;
import java.util.List;

public class ChatRoomMain {
  record ChatRoom(Context context,
                  List<Actor<Session>> sessions) {
    public void getSession(String screenName, Actor<Gabbler> client) {
      // create a child actor for further interaction with the client
      var room = context.currentActor(ChatRoom.class);
      var session = Actor.of(Session.class, screenName)
          .behavior(context -> new Session(context, room, screenName, client));
      sessions.add(session);
      context.spawn(session);
      context.postTo(client, $ -> $.sessionGranted(session));
    }

    public void publishSessionMessage(String screenName, String message) {
      for(var session: sessions) {
        context.postTo(session, $ -> $.notifyClient(screenName, message));
      }
    }
  }

  record Gabbler(Context context) {
    public void sessionDenied(String reason) {
      System.err.println("cannot start chat room session: " + reason);
      context.shutdown();
    }

    public void sessionGranted(Actor<Session> session) {
      context.postTo(session, $ -> $.postMessage("Hello World !"));
    }

    public void messagePosted(String screenName, String message) {
      System.out.println("message has been posted by " + screenName + " " + message);
      context.shutdown();
    }
  }

  record Session(Context context,
                 Actor<ChatRoom> room, String screenName, Actor<Gabbler> client) {
    public void postMessage(String message) {
      context.postTo(room, $ -> $.publishSessionMessage(screenName, message));
    }

    public void notifyClient(String screenName, String message) {
      // published from the room
      context.postTo(client, $ -> $.messagePosted(screenName, message));
    }
  }

  public static void main(String[] args) throws InterruptedException {
    var chatRoom = Actor.of(ChatRoom.class)
        .behavior(context -> new ChatRoom(context, new ArrayList<>()));
    var gabbler = Actor.of(Gabbler.class)
        .behavior(Gabbler::new)
        .onSignal((signal, context) -> context.signal(chatRoom, ShutdownSignal.INSTANCE));

    Actor.run(List.of(chatRoom, gabbler), context -> {
      context.postTo(chatRoom, $ -> $.getSession("ol’ Gabbler", gabbler));
    });
  }
}
