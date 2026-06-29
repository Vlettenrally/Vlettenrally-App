package nl.vlettenrally.websockets;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Publisher for WebSocket messages using Valkey pub/sub for cross-pod distribution.
 * 
 * Services can inject this class and use the publish methods to send messages
 * to connected WebSocket clients. Messages are published to a Valkey channel
 * and delivered to all API pods, which then forward them to their locally
 * connected WebSocket sessions based on targeting (game, team, admin-only).
 */
@ApplicationScoped
public class WebSocketPublisher {
    
    @Inject
    ValkeyPubSubService valkeyPubSubService;
    
    /**
     * Publish a message to all connected clients
     */
    public void broadcast(String type, Object payload) {
        WebSocketMessage message = new WebSocketMessage(type, payload);
        valkeyPubSubService.publish(message);
    }
    
    /**
     * Publish a message to all clients in a specific game
     */
    public void broadcastToGame(UUID gameId, String type, Object payload) {
        WebSocketMessage message = new WebSocketMessage(type, payload)
            .withTargetGame(gameId);
        valkeyPubSubService.publish(message);
    }
    
    /**
     * Publish a message to a specific team
     */
    public void sendToTeam(UUID gameId, UUID teamId, String type, Object payload) {
        WebSocketMessage message = new WebSocketMessage(type, payload)
            .withTargetGame(gameId)
            .withTargetTeam(teamId);
        valkeyPubSubService.publish(message);
    }
    
    /**
     * Publish a message to a specific team, excluding admin sessions.
     */
    public void sendToTeamOnly(UUID gameId, UUID teamId, String type, Object payload) {
        WebSocketMessage message = new WebSocketMessage(type, payload)
            .withTargetGame(gameId)
            .withTargetTeam(teamId)
            .forTeamOnly();
        valkeyPubSubService.publish(message);
    }

    /**
     * Publish a message to admins only for a specific game
     */
    public void sendToAdmins(UUID gameId, String type, Object payload) {
        WebSocketMessage message = new WebSocketMessage(type, payload)
            .withTargetGame(gameId)
            .forAdminOnly();
        valkeyPubSubService.publish(message);
    }
    
    /**
     * Publish a custom message (for advanced targeting)
     */
    public void publish(WebSocketMessage message) {
        valkeyPubSubService.publish(message);
    }
}
