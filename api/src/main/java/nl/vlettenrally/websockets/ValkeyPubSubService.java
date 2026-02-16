package nl.vlettenrally.websockets;

import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.logging.Log;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.pubsub.ReactivePubSubCommands;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.websocket.Session;

/**
 * Valkey (Redis-compatible) pub/sub service for cross-pod WebSocket message distribution.
 * <p>
 * Each API pod publishes WebSocket messages to a Valkey channel. All pods subscribe to
 * the same channel and deliver incoming messages to their locally connected WebSocket sessions.
 * This enables horizontal scaling of the API while ensuring all connected clients receive
 * messages regardless of which pod they are connected to.
 */
@ApplicationScoped
public class ValkeyPubSubService {

    private static final String CHANNEL = "ws:messages";

    @Inject
    ReactiveRedisDataSource redisDataSource;

    @Inject
    WebSocketSessionManager sessionManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ReactivePubSubCommands<String> subscriber;

    /**
     * Subscribe to the Valkey pub/sub channel on startup.
     * Messages received from the channel are delivered to matching local WebSocket sessions.
     */
    protected void onStart(@Observes StartupEvent event) {
        subscriber = redisDataSource.pubsub(String.class);

        subscriber.subscribe(CHANNEL, jsonMessage -> {
            try {
                deliverLocally(jsonMessage);
            } catch (Exception e) {
                Log.error("Failed to deliver Valkey pub/sub message locally: " + e.getMessage(), e);
            }
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
          .subscribe().with(
              sub -> Log.infof("Subscribed to Valkey channel '%s' for WebSocket message distribution", CHANNEL),
              error -> Log.errorf("Failed to subscribe to Valkey channel '%s': %s", CHANNEL, error.getMessage())
          );
    }

    protected void onStop(@Observes ShutdownEvent event) {
        Log.info("Shutting down Valkey pub/sub subscription");
    }

    /**
     * Publish a WebSocket message to the Valkey channel for cross-pod distribution.
     * The message is serialized to JSON once and sent as a string.
     */
    public void publish(WebSocketMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            redisDataSource.pubsub(String.class)
                .publish(CHANNEL, json)
                .subscribe().with(
                    count -> Log.debugf("Published WebSocket message type=%s to %d subscriber(s)", message.getType(), count),
                    error -> Log.errorf("Failed to publish to Valkey: %s", error.getMessage())
                );
        } catch (JsonProcessingException e) {
            Log.error("Failed to serialize WebSocket message for Valkey: " + e.getMessage(), e);
        }
    }

    /**
     * Deliver a message received from Valkey to matching local WebSocket sessions.
     * <p>
     * We parse only the routing metadata (targetGameId, targetTeamId, adminOnly, targetSessionId) to determine
     * which local sessions should receive the message. The original pre-serialized JSON is
     * forwarded directly to avoid re-serialization and Object-type payload issues.
     */
    protected void deliverLocally(String jsonMessage) {
        // Parse routing metadata from the JSON
        JsonNode node;
        try {
            node = objectMapper.readTree(jsonMessage);
        } catch (JsonProcessingException e) {
            Log.error("Failed to parse Valkey message JSON: " + e.getMessage());
            return;
        }

        // Check for session-specific targeting (e.g., PONG responses)
        if (node.has("targetSessionId") && !node.get("targetSessionId").isNull()) {
            String targetSessionId = node.get("targetSessionId").asText();
            WebSocketSession wsSession = sessionManager.getSession(targetSessionId);
            if (wsSession != null) {
                sendToSession(wsSession.getSession(), jsonMessage);
            }
            return; // Session-specific message, don't broadcast further
        }

        // Build a lightweight WebSocketMessage with only routing fields for session filtering
        WebSocketMessage routingMessage = new WebSocketMessage();
        routingMessage.setType(node.has("type") ? node.get("type").asText() : null);
        routingMessage.setAdminOnly(node.has("adminOnly") && node.get("adminOnly").asBoolean());

        if (node.has("targetGameId") && !node.get("targetGameId").isNull()) {
            routingMessage.setTargetGameId(UUID.fromString(node.get("targetGameId").asText()));
        }
        if (node.has("targetTeamId") && !node.get("targetTeamId").isNull()) {
            routingMessage.setTargetTeamId(UUID.fromString(node.get("targetTeamId").asText()));
        }

        // Find matching local sessions and forward the pre-serialized JSON
        var targetSessions = sessionManager.getTargetSessions(routingMessage);

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
