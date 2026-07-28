package nl.vlettenrally.websockets;

import jakarta.websocket.Session;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a WebSocket session with associated metadata
 */
@Getter
public class WebSocketSession {
    
    public enum SessionType {
        TEAM,
        ADMIN
    }
    
    private final Session session;
    private final SessionType type;
    private UUID gameId; // mutable for admin sessions to switch games
    private final UUID teamId; // null for admin sessions
    private UUID checkpointId; // optional: tracks the checkpoint an admin is currently manning
    private Instant lastActivity;
    
    public WebSocketSession(Session session, SessionType type, UUID gameId, UUID teamId) {
        this.session = session;
        this.type = type;
        this.gameId = gameId;
        this.teamId = teamId;
        this.lastActivity = Instant.now();
    }
    
    public static WebSocketSession forTeam(Session session, UUID gameId, UUID teamId) {
        return new WebSocketSession(session, SessionType.TEAM, gameId, teamId);
    }
    
    /**
     * Create an admin session for a specific game
     */
    public static WebSocketSession forAdmin(Session session, UUID gameId) {
        return new WebSocketSession(session, SessionType.ADMIN, gameId, null);
    }
    
    /**
     * Create an admin session without a game (game-agnostic, must call setGameId to receive messages)
     */
    public static WebSocketSession forAdmin(Session session) {
        return new WebSocketSession(session, SessionType.ADMIN, null, null);
    }

    /**
     * Update the last activity timestamp (called when PING is received)
     */
    public void updateActivity() {
        this.lastActivity = Instant.now();
    }
    
    /**
     * Set the game ID for admin sessions (used for SWITCH_GAME)
     * @throws IllegalStateException if called on a team session
     */
    public void setGameId(UUID gameId) {
        if (!isAdmin()) {
            throw new IllegalStateException("Cannot change gameId for team sessions");
        }
        this.gameId = gameId;
    }

    /**
     * Set the checkpoint the admin is currently manning. May be null when the
     * admin clears their selection.
     */
    public void setCheckpointId(UUID checkpointId) {
        if (!isAdmin()) {
            throw new IllegalStateException("Cannot set checkpointId for team sessions");
        }
        this.checkpointId = checkpointId;
    }
    
    public boolean isAdmin() {
        return type == SessionType.ADMIN;
    }
    
    public boolean isTeam() {
        return type == SessionType.TEAM;
    }
    
    public String getSessionId() {
        return session.getId();
    }
    
    /**
     * Check if this session should receive a message based on targeting
     */
    public boolean shouldReceive(WebSocketMessage message) {
        // Admin sessions with no game selected receive nothing until SWITCH_GAME
        if (isAdmin() && gameId == null) {
            return false;
        }
        
        // Check game targeting
        if (message.getTargetGameId() != null && !message.getTargetGameId().equals(gameId)) {
            return false;
        }
        
        // Check team-only messages (admin sessions must not receive these)
        if (message.isTeamOnly() && isAdmin()) {
            return false;
        }

        // Check admin-only messages
        if (message.isAdminOnly() && !isAdmin()) {
            return false;
        }
        
        // A team-targeted frame is for that team alone. Admins get their per-team view through
        // the heartbeat-gated admin topics, not by eavesdropping on team traffic.
        if (message.getTargetTeamId() != null) {
            return message.getTargetTeamId().equals(teamId);
        }
        
        return true;
    }
}
