package nl.vlettenrally.websockets;

import jakarta.inject.Inject;
import jakarta.websocket.Session;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;

import java.util.UUID;

/**
 * WebSocket endpoint for team connections
 * 
 * Teams connect to: /ws/game/{gameId}/team/{teamId}
 */
@ServerEndpoint("/ws/game/{gameId}/team/{teamId}")
public class TeamWebSocket {
    
    @Inject
    WebSocketSessionManager sessionManager;
    
    @OnOpen
    public void onOpen(Session session, 
                       @PathParam("gameId") String gameId, 
                       @PathParam("teamId") String teamId) {
        try {
            UUID gameUuid = UUID.fromString(gameId);
            UUID teamUuid = UUID.fromString(teamId);
            
            WebSocketSession wsSession = WebSocketSession.forTeam(session, gameUuid, teamUuid);
            sessionManager.addSession(wsSession);
            
            System.out.println("Team WebSocket opened - Game: " + gameId + ", Team: " + teamId + ", Session: " + session.getId());
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid UUID in WebSocket connection: " + e.getMessage());
            try {
                session.close();
            } catch (Exception ex) {
                // ignore
            }
        }
    }

    @OnClose
    public void onClose(Session session) {
        sessionManager.removeSession(session.getId());
        System.out.println("Team WebSocket closed: " + session.getId());
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        System.err.println("Team WebSocket error on session " + session.getId() + ": " + throwable.getMessage());
        sessionManager.removeSession(session.getId());
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        WebSocketSession wsSession = sessionManager.getSession(session.getId());
        if (wsSession != null) {
            System.out.println("Team WebSocket message from Team " + wsSession.getTeamId() + ": " + message);
        }
    }
}
