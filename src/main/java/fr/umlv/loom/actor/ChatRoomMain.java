package fr.umlv.loom.actor;

import fr.umlv.loom.actor.Actor.Context;
import fr.umlv.loom.actor.Actor.PanicSignal;
import fr.umlv.loom.actor.Actor.ShutdownSignal;

import java.util.ArrayList;
import java.util.List;

public class ChatRoomMain {
  interface ChatRoom {
    void getSession(String screenName, Actor<Gabbler> client);
    void publishSessionMessage(String screenName, String message);
  }

  interface Gabbler {
    void sessionGranted(Actor<Session> sessionActor);
    void sessionDenied(String reason);
    void messagePosted(String screenName, String message);
  }

  interface Session {
    void postMessage(String message);
    void notifyClient(String screenName, String message);
  }

  private static ChatRoom chatRoomBehavior(Context context) {
    return new ChatRoom() {
      private final ArrayList<Actor<Session>> sessions = new ArrayList<>();

      @Override
      public void getSession(String screenName, Actor<Gabbler> client) {
        // create a child actor for further interaction with the client
        var room = context.currentActor(ChatRoom.class);
        var session = Actor.of(Session.class, screenName)
            .behavior(context -> sessionBehavior(context, room, screenName, client));
        sessions.add(session);
        context.spawn(session);
        context.postTo(client, $ -> $.sessionGranted(session));
      }

      @Override
      public void publishSessionMessage(String screenName, String message) {
        for(var session: sessions) {
          context.postTo(session, $ -> $.notifyClient(screenName, message));
        }
      }
    };
  }

  private static Gabbler gabblerBehavior(Context context) {
    return new Gabbler() {
      @Override
      public void sessionDenied(String reason) {
        System.err.println("cannot start chat room session: " + reason);
        context.shutdown();
      }

      @Override
      public void sessionGranted(Actor<Session> session) {
        context.postTo(session, $ -> $.postMessage("Hello World !"));
      }

      @Override
      public void messagePosted(String screenName, String message) {
        System.out.println("message has been posted by " + screenName + " " + message);
        context.shutdown();
      }
    };
  }

  private static Session sessionBehavior(Context context,
                                         Actor<ChatRoom> room, String screenName, Actor<Gabbler> client) {
    return new Session() {
      @Override
      public void postMessage(String message) {
        context.postTo(room, $ -> $.publishSessionMessage(screenName, message));
      }

      @Override
      public void notifyClient(String screenName, String message) {
        // published from the room
        context.postTo(client, $ -> $.messagePosted(screenName, message));
      }
    };
  }

  public static void main(String[] args) throws InterruptedException {
    var chatRoom = Actor.of(ChatRoom.class)
        .behavior(ChatRoomMain::chatRoomBehavior);
    var gabbler = Actor.of(Gabbler.class)
        .behavior(ChatRoomMain::gabblerBehavior)
        .onSignal((signal, context) -> context.signal(chatRoom, ShutdownSignal.INSTANCE));

    Actor.run(List.of(chatRoom, gabbler), context -> {
      context.postTo(chatRoom, $ -> $.getSession("ol’ Gabbler", gabbler));
    });
  }
}
