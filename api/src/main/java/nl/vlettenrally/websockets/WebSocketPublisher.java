package nl.vlettenrally.websockets;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.websocket.Session;

import java.util.UUID;

/**
 * Publisher for WebSocket messages using CDI events (pub/sub pattern)
 * 
 * Services can inject this class and use the publish methods to send messages
 * to connected WebSocket clients. The messages are distributed based on their
 * targeting (game, team, admin-only).
 */
@ApplicationScoped
public class WebSocketPublisher {
    
    @Inject
    WebSocketSessionManager sessionManager;
    
    @Inject
    Event<WebSocketMessage> messageEvent;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Publish a message to all connected clients
     */
    public void broadcast(String type, Object payload) {
        WebSocketMessage message = new WebSocketMessage(type, payload);
        messageEvent.fire(message);
    }
    
    /**
     * Publish a message to all clients in a specific game
     */
    public void broadcastToGame(UUID gameId, String type, Object payload) {
        WebSocketMessage message = new WebSocketMessage(type, payload)
            .withTargetGame(gameId);
        messageEvent.fire(message);
    }
    
    /**
     * Publish a message to a specific team
     */
    public void sendToTeam(UUID gameId, UUID teamId, String type, Object payload) {
        WebSocketMessage message = new WebSocketMessage(type, payload)
            .withTargetGame(gameId)
            .withTargetTeam(teamId);
        messageEvent.fire(message);
    }
    
    /**
     * Publish a message to admins only for a specific game
     */
    public void sendToAdmins(UUID gameId, String type, Object payload) {
        WebSocketMessage message = new WebSocketMessage(type, payload)
            .withTargetGame(gameId)
            .forAdminOnly();
        messageEvent.fire(message);
    }
    
    /**
     * Publish a custom message (for advanced targeting)
     */
    public void publish(WebSocketMessage message) {
        messageEvent.fire(message);
    }
    
    /**
     * CDI event observer - handles actual message delivery
     */
    void onMessage(@Observes WebSocketMessage message) {
        var targetSessions = sessionManager.getTargetSessions(message);
        
        String jsonMessage;
        try {
            jsonMessage = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            Log.error("Failed to serialize WebSocket message: " + e.getMessage());
            return;
        }
        
        for (WebSocketSession wsSession : targetSessions) {
            sendToSession(wsSession.getSession(), jsonMessage);
        }
    }
    
    private void sendToSession(Session session, String message) {
        if (session.isOpen()) {
            session.getAsyncRemote().sendText(message, result -> {
                if (!result.isOK()) {
                    Log.error("Error sending WebSocket message: " + result.getException());
                }
            });
        }
    }
}
