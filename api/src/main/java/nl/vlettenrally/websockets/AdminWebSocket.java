package nl.vlettenrally.websockets;

import jakarta.inject.Inject;
import jakarta.websocket.Session;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.logging.Log;

import java.util.UUID;

/**
 * WebSocket endpoint for admin connections
 * 
 * Admins connect to: /ws/game/{gameId}/admin
 */
@ServerEndpoint("/ws/game/{gameId}/admin")
public class AdminWebSocket {
    
    @Inject
    WebSocketSessionManager sessionManager;
    
    @ConfigProperty(name = "vlettenrally.websocket.idle-timeout-seconds", defaultValue = "60")
    int idleTimeoutSeconds;
    
    @OnOpen
    public void onOpen(Session session, @PathParam("gameId") String gameId) {
        try {
            UUID gameUuid = UUID.fromString(gameId);
            
            // Set idle timeout for this session
            session.setMaxIdleTimeout(idleTimeoutSeconds * 1000L);
            
            WebSocketSession wsSession = WebSocketSession.forAdmin(session, gameUuid);
            sessionManager.addSession(wsSession);
            
            Log.info("Admin WebSocket opened - Game: " + gameId + ", Session: " + session.getId());
        } catch (IllegalArgumentException e) {
            Log.error("Invalid UUID in WebSocket connection: " + e.getMessage());
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
        Log.info("Admin WebSocket closed: " + session.getId());
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        Log.error("Admin WebSocket error on session " + session.getId() + ": " + throwable.getMessage());
        sessionManager.removeSession(session.getId());
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        // Ignore heartbeat messages from client
        if (message != null && message.contains("HEARTBEAT")) {
            return;
        }
        
        WebSocketSession wsSession = sessionManager.getSession(session.getId());
        if (wsSession != null) {
            Log.info("Admin WebSocket message from Game " + wsSession.getGameId() + ": " + message);
        }
    }
}
