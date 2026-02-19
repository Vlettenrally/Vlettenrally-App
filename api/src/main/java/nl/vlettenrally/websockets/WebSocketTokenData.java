package nl.vlettenrally.websockets;

import java.time.Instant;
import java.util.UUID;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Data stored with a WebSocket token in Valkey.
 * Contains all information needed to establish a WebSocket session.
 */
@RegisterForReflection
public record WebSocketTokenData(
    String role,           // "admin" or "team"
    UUID userId,           // admin ID or team ID
    String userName,       // admin name or team name
    UUID gameId,           // null for admin, required for team
    UUID teamId,           // null for admin, required for team
    Instant createdAt
) {
    public boolean isAdmin() {
        return "admin".equals(role);
    }

    public boolean isTeam() {
        return "team".equals(role);
    }

    /**
     * Create token data for an admin user.
     */
    public static WebSocketTokenData forAdmin(UUID adminId, String adminName) {
        return new WebSocketTokenData("admin", adminId, adminName, null, null, Instant.now());
    }

    /**
     * Create token data for a team.
     */
    public static WebSocketTokenData forTeam(UUID teamId, String teamName, UUID gameId) {
        return new WebSocketTokenData("team", teamId, teamName, gameId, teamId, Instant.now());
    }
}

