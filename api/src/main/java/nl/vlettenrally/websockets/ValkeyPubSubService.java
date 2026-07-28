package nl.vlettenrally.websockets;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
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

    /**
     * A publish failure is a symptom of an overloaded system, so logging every one of them
     * adds cost exactly when there is none to spare. Drops are counted on a metric; the log
     * only carries a sample.
     */
    private static final long FAILURE_LOG_INTERVAL_MILLIS = 10_000;

    /** Ceiling for the re-subscribe backoff, so a long Valkey outage doesn't spin. */
    private static final Duration MAX_RESUBSCRIBE_BACKOFF = Duration.ofSeconds(30);

    private static final AttributeKey<String> REASON = AttributeKey.stringKey("reason");
    private static final Attributes POOL_EXHAUSTED = Attributes.of(REASON, "pool_exhausted");
    private static final Attributes OTHER_FAILURE = Attributes.of(REASON, "other");

    @Inject
    ReactiveRedisDataSource redisDataSource;

    @Inject
    WebSocketSessionManager sessionManager;

    @Inject
    ObjectMapper objectMapper;

    private ReactivePubSubCommands<String> subscriber;
    private ReactivePubSubCommands<String> publisher;

    private LongCounter publishOk;
    private LongCounter publishFailed;

    /** Epoch millis of the last emitted failure log, per reason. */
    private final Map<String, AtomicLong> lastFailureLog = Map.of(
            "pool_exhausted", new AtomicLong(0),
            "other", new AtomicLong(0));

    private volatile boolean shuttingDown;

    /** Guards against more than one re-subscribe attempt being scheduled at a time. */
    private final java.util.concurrent.atomic.AtomicBoolean retryPending =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    /**
     * Subscribe to the Valkey pub/sub channel on startup.
     * Messages received from the channel are delivered to matching local WebSocket sessions.
     */
    protected void onStart(@Observes StartupEvent event) {
        subscriber = redisDataSource.pubsub(String.class);
        publisher = redisDataSource.pubsub(String.class);

        var meter = GlobalOpenTelemetry.getMeter("nl.vlettenrally.websockets");
        publishOk = meter.counterBuilder("ws.publish.ok")
                .setDescription("WebSocket messages successfully published to Valkey")
                .build();
        publishFailed = meter.counterBuilder("ws.publish.failed")
                .setDescription("WebSocket messages dropped because publishing to Valkey failed")
                .build();

        subscribe(Duration.ofSeconds(1));
    }

    protected void onStop(@Observes ShutdownEvent event) {
        shuttingDown = true;
        Log.info("Shutting down Valkey pub/sub subscription");
    }

    /**
     * Subscribe to the channel, re-subscribing with backoff if the connection ends or errors.
     * Without this, a sentinel failover silently ends the subscription and the pod stops
     * receiving cross-pod messages for the rest of its lifetime.
     */
    private void subscribe(Duration backoff) {
        if (shuttingDown) {
            return;
        }
        subscriber.subscribe(CHANNEL,
                jsonMessage -> {
                    try {
                        deliverLocally(jsonMessage);
                    } catch (RuntimeException e) {
                        Log.error("Failed to deliver Valkey pub/sub message locally: " + e.getMessage(), e);
                    }
                },
                () -> {
                    if (!shuttingDown) {
                        Log.warnf("Valkey subscription to '%s' ended; re-subscribing", CHANNEL);
                        resubscribe(Duration.ofSeconds(1));
                    }
                },
                error -> {
                    if (!shuttingDown) {
                        Log.errorf("Valkey subscription to '%s' failed: %s; re-subscribing", CHANNEL, error.getMessage());
                        resubscribe(Duration.ofSeconds(1));
                    }
                })
            .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
            .subscribe().with(
                sub -> Log.infof("Subscribed to Valkey channel '%s' for WebSocket message distribution", CHANNEL),
                error -> {
                    Log.errorf("Failed to subscribe to Valkey channel '%s': %s", CHANNEL, error.getMessage());
                    resubscribe(backoff);
                }
            );
    }

    /**
     * Retry the subscription after a delay, doubling the backoff up to a 30s ceiling.
     * <p>
     * At most one retry may be in flight. A single failure can trigger both the onEnd and the
     * onException callback, and without this guard each round would fork another retry chain —
     * doubling the number of pending subscriptions on every attempt.
     */
    private void resubscribe(Duration backoff) {
        if (shuttingDown || !retryPending.compareAndSet(false, true)) {
            return;
        }
        Duration next = backoff.multipliedBy(2);
        Duration capped = next.compareTo(MAX_RESUBSCRIBE_BACKOFF) > 0 ? MAX_RESUBSCRIBE_BACKOFF : next;
        Infrastructure.getDefaultWorkerPool().execute(() -> {
            try {
                Thread.sleep(backoff.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } finally {
                retryPending.set(false);
            }
            subscribe(capped);
        });
    }

    /**
     * Publish a WebSocket message to the Valkey channel for cross-pod distribution.
     * The message is serialized to JSON once and sent as a string.
     * <p>
     * Delivery is best-effort: a failed publish is counted and dropped, never retried. These
     * are live position and score updates that self-heal on the next broadcast tick, and
     * retrying would add load to a system that is by definition already saturated.
     */
    public void publish(WebSocketMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            publisher.publish(CHANNEL, json)
                .subscribe().with(
                    count -> {
                        publishOk.add(1);
                        Log.debugf("Published WebSocket message type=%s to %d subscriber(s)", message.getType(), count);
                    },
                            this::recordFailure
                );
        } catch (JsonProcessingException e) {
            Log.error("Failed to serialize WebSocket message for Valkey: " + e.getMessage(), e);
        }
    }

    private void recordFailure(Throwable error) {
        String detail = error.getMessage() == null ? "" : error.getMessage();
        boolean poolExhausted = detail.contains("max wait queue size") || detail.contains("Timeout");
        String reason = poolExhausted ? "pool_exhausted" : "other";

        publishFailed.add(1, poolExhausted ? POOL_EXHAUSTED : OTHER_FAILURE);

        AtomicLong last = lastFailureLog.get(reason);
        long now = System.currentTimeMillis();
        long previous = last.get();
        if (now - previous >= FAILURE_LOG_INTERVAL_MILLIS && last.compareAndSet(previous, now)) {
            Log.errorf("Failed to publish to Valkey (%s): %s — further occurrences suppressed for %ds,"
                    + " see the ws.publish.failed metric", reason, detail, FAILURE_LOG_INTERVAL_MILLIS / 1000);
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

        // A multiplexed envelope holds every team's payload, so it is never forwarded as-is.
        if (hasValue(node, "perTeamPayloads")) {
            deliverMultiplexed(node);
            return;
        }

        // Multi-session targeting: one publish carrying the same payload for a set of sessions.
        if (hasValue(node, "targetSessionIds")) {
            deliverToSessionList(node);
            return;
        }

        // Check for session-specific targeting (e.g., PONG responses)
        if (hasValue(node, "targetSessionId")) {
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
        routingMessage.setTeamOnly(node.has("teamOnly") && node.get("teamOnly").asBoolean());

        if (hasValue(node, "targetGameId")) {
            routingMessage.setTargetGameId(UUID.fromString(node.get("targetGameId").asText()));
        }
        if (hasValue(node, "targetTeamId")) {
            routingMessage.setTargetTeamId(UUID.fromString(node.get("targetTeamId").asText()));
        }

        // Find matching local sessions and forward the pre-serialized JSON
        var targetSessions = sessionManager.getTargetSessions(routingMessage);

        for (WebSocketSession wsSession : targetSessions) {
            sendToSession(wsSession.getSession(), jsonMessage);
        }
    }

    /**
     * Slice a multiplexed envelope into a per-team frame for each locally connected team.
     * Frames are built once per team, not once per session, so a team's multiple devices
     * share one serialization.
     */
    private void deliverMultiplexed(JsonNode node) {
        JsonNode perTeam = node.get("perTeamPayloads");
        UUID gameId = hasValue(node, "targetGameId")
                ? UUID.fromString(node.get("targetGameId").asText())
                : null;
        String type = node.has("type") ? node.get("type").asText() : null;
        String timestamp = hasValue(node, "timestamp") ? node.get("timestamp").asText() : null;

        Map<UUID, String> frames = new HashMap<>();
        for (WebSocketSession wsSession : sessionManager.getAllSessions()) {
            // Admins never receive team-only traffic, and the envelope is game-scoped.
            if (!wsSession.isTeam() || wsSession.getTeamId() == null) {
                continue;
            }
            if (gameId != null && !gameId.equals(wsSession.getGameId())) {
                continue;
            }
            UUID teamId = wsSession.getTeamId();
            JsonNode slice = perTeam.get(teamId.toString());
            if (slice == null || slice.isNull()) {
                continue;
            }
            String frame = frames.computeIfAbsent(teamId, id -> {
                ObjectNode out = objectMapper.createObjectNode();
                out.put("type", type);
                out.set("payload", slice);
                if (timestamp != null) {
                    out.put("timestamp", timestamp);
                }
                if (gameId != null) {
                    out.put("targetGameId", gameId.toString());
                }
                out.put("targetTeamId", id.toString());
                out.put("teamOnly", true);
                return out.toString();
            });
            sendToSession(wsSession.getSession(), frame);
        }
    }

    /**
     * Deliver one identical payload to an explicit set of sessions. The routing field is
     * stripped before the frame reaches a client — it would otherwise hand every recipient
     * the other recipients' session ids, which {@link WebSocketPingHandler} treats as
     * adoptable.
     */
    private void deliverToSessionList(JsonNode node) {
        ObjectNode out = node.deepCopy();
        JsonNode ids = out.remove("targetSessionIds");
        String frame = out.toString();

        for (JsonNode id : ids) {
            WebSocketSession wsSession = sessionManager.getSession(id.asText());
            if (wsSession != null) {
                sendToSession(wsSession.getSession(), frame);
            }
        }
    }

    private static boolean hasValue(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull();
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
