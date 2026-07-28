package nl.vlettenrally.websockets;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a WebSocket message that can be sent to clients
 */
@Setter
@Getter
@RegisterForReflection
public class WebSocketMessage {

    /**
     * Sentinel {@code targetTeamId} carried by multiplexed envelopes. No real team has it, so
     * a pod running an older build — which does not understand {@link #perTeamPayloads} — routes
     * the envelope to zero sessions instead of forwarding every team's slice to every team.
     * This keeps a rolling deploy safe: the envelope is simply undeliverable on old pods.
     */
    static final UUID MULTIPLEX_SENTINEL_TEAM_ID = new UUID(0L, 0L);

    // Getters and Setters
    private String type;
    private Object payload;
    private String timestamp;
    private UUID targetGameId;
    private UUID targetTeamId;
    private boolean adminOnly;
    private boolean teamOnly;
    private String targetSessionId; // For session-specific messages like PONG

    /**
     * Identical payload delivered to a specific set of sessions, as one publish instead of
     * one per session. Stripped before the frame reaches a client.
     */
    private List<String> targetSessionIds;

    /**
     * Distinct payload per team carried in a single publish, so an O(teams) fan-out costs one
     * Valkey round-trip instead of one per team. The receiving pod slices out each locally
     * connected team's own entry — the envelope itself is never forwarded to a client, since
     * it holds every team's view.
     */
    private Map<UUID, Object> perTeamPayloads;

    public WebSocketMessage() {
        this.timestamp = Instant.now().toString();
    }

    public WebSocketMessage(String type, Object payload) {
        this();
        this.type = type;
        this.payload = payload;
    }

    // Builder-style methods for fluent API
    public WebSocketMessage withTargetGame(UUID gameId) {
        this.targetGameId = gameId;
        return this;
    }

    public WebSocketMessage withTargetTeam(UUID teamId) {
        this.targetTeamId = teamId;
        return this;
    }

    public WebSocketMessage forAdminOnly() {
        this.adminOnly = true;
        return this;
    }

    public WebSocketMessage forTeamOnly() {
        this.teamOnly = true;
        return this;
    }
    
    public WebSocketMessage withTargetSession(String sessionId) {
        this.targetSessionId = sessionId;
        return this;
    }

    /**
     * Target a set of sessions with one publish. All listed sessions receive the same payload.
     */
    public WebSocketMessage withTargetSessions(Collection<String> sessionIds) {
        this.targetSessionIds = List.copyOf(sessionIds);
        return this;
    }

    /**
     * Build a single envelope carrying a distinct payload per team, delivered team-only within
     * one game. Each receiving pod forwards only the entry belonging to each of its locally
     * connected teams.
     */
    public static WebSocketMessage multiplexed(String type, UUID gameId, Map<UUID, Object> perTeam) {
        WebSocketMessage message = new WebSocketMessage();
        message.type = type;
        message.perTeamPayloads = Map.copyOf(perTeam);
        message.targetGameId = gameId;
        message.teamOnly = true;
        message.targetTeamId = MULTIPLEX_SENTINEL_TEAM_ID;
        return message;
    }

}
