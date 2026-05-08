package nl.vlettenrally.websockets;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Metadata for a WebSocket session stored in Valkey for cross-pod session tracking.
 * This allows any pod to look up session information and send responses via pub/sub.
 *
 * @param sessionType "TEAM" or "ADMIN"
 * @param teamId      null for admin sessions
 * @param adminId     null for team sessions
 */
@RegisterForReflection
public record WebSocketSessionMetadata(String sessionId, String sessionType, UUID gameId, UUID teamId, UUID adminId) {

    @JsonCreator
    public WebSocketSessionMetadata(
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("sessionType") String sessionType,
            @JsonProperty("gameId") UUID gameId,
            @JsonProperty("teamId") UUID teamId,
            @JsonProperty("adminId") UUID adminId) {
        this.sessionId = sessionId;
        this.sessionType = sessionType;
        this.gameId = gameId;
        this.teamId = teamId;
        this.adminId = adminId;
    }

    public static WebSocketSessionMetadata forTeam(String sessionId, UUID gameId, UUID teamId) {
        return new WebSocketSessionMetadata(sessionId, "TEAM", gameId, teamId, null);
    }

    public static WebSocketSessionMetadata forAdmin(String sessionId, UUID gameId, UUID adminId) {
        return new WebSocketSessionMetadata(sessionId, "ADMIN", gameId, null, adminId);
    }

    public static WebSocketSessionMetadata forAdminNoGame(String sessionId, UUID adminId) {
        return new WebSocketSessionMetadata(sessionId, "ADMIN", null, null, adminId);
    }

    public boolean isTeam() {
        return "TEAM".equals(sessionType);
    }

    public boolean isAdmin() {
        return "ADMIN".equals(sessionType);
    }

    /**
     * Create a new metadata instance with updated game ID (for admin game switching)
     */
    public WebSocketSessionMetadata withGameId(UUID newGameId) {
        return new WebSocketSessionMetadata(sessionId, sessionType, newGameId, teamId, adminId);
    }
}
