package nl.vlettenrally.websockets;

import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Publisher for WebSocket messages using Valkey pub/sub for cross-pod distribution.
 * <p>
 * Services can inject this class and use the publish methods to send messages
 * to connected WebSocket clients. Messages are published to a Valkey channel
 * and delivered to all API pods, which then forward them to their locally
 * connected WebSocket sessions based on targeting (game, team, admin-only).
 */
@ApplicationScoped
public class WebSocketPublisher {
    
    @Inject
    ValkeyPubSubService valkeyPubSubService;

    @Inject
    AdminTopicSubscriptionManager adminTopicSubscriptionManager;
    
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
     * Publish a message to a specific team, and to that team alone — admin sessions never
     * receive it. Admins get their per-team view through {@link #sendToAdminTopic}.
     * <p>
     * {@code teamOnly} is redundant against the current routing rule, which already treats a
     * {@code targetTeamId} as exclusive. It is set for the sake of a rolling deploy: a pod still
     * running the previous rule forwarded team-targeted frames to every admin in the game, and
     * honours this flag instead. Same hedge as {@link WebSocketMessage#MULTIPLEX_SENTINEL_TEAM_ID}.
     */
    public void sendToTeam(UUID gameId, UUID teamId, String type, Object payload) {
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
     * Publish a message to admins who are actively viewing a given sub-page
     * ("topic") for a game. Only sessions that have sent an ADMIN_HEARTBEAT for
     * the topic within the heartbeat window receive it; this avoids streaming
     * page-specific events to admins who aren't on that page.
     *
     * @see AdminTopicSubscriptionManager
     */
    public void sendToAdminTopic(UUID gameId, String topic, String type, Object payload) {
        sendToAdminTopicLazily(gameId, topic, type, () -> payload);
    }

    /**
     * As {@link #sendToAdminTopic(UUID, String, String, Object)}, but the payload is only built
     * when at least one admin is actually subscribed. Use this when producing the payload is
     * itself expensive — the ranking payload, for instance, costs one points lookup per team,
     * which would otherwise be paid on every broadcast tick with nobody on the page.
     * <p>
     * Deliberately a distinct name rather than an overload: {@code Supplier<Object>} is more
     * specific than {@code Object}, so an overload would silently capture every call site that
     * passes a lambda or a Mockito matcher.
     */
    public void sendToAdminTopicLazily(UUID gameId, String topic, String type, Supplier<Object> payload) {
        Set<String> subscribers = adminTopicSubscriptionManager.getActiveSubscribers(gameId, topic);
        if (subscribers.isEmpty()) {
            return;
        }
        // One publish for the whole set: the receiving pod fans out locally.
        WebSocketMessage message = new WebSocketMessage(type, payload.get())
            .withTargetGame(gameId)
            .forAdminOnly()
            .withTargetSessions(subscribers);
        valkeyPubSubService.publish(message);
    }

    /**
     * Publish a custom message (for advanced targeting)
     */
    public void publish(WebSocketMessage message) {
        valkeyPubSubService.publish(message);
    }
}
