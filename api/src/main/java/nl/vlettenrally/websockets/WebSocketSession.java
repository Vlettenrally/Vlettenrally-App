package nl.vlettenrally.websockets;

import jakarta.websocket.Session;
import java.util.UUID;

/**
 * Represents a WebSocket session with associated metadata
 */
public class WebSocketSession {
    
    public enum SessionType {
        TEAM,
        ADMIN
    }
    
    private final Session session;
    private final SessionType type;
    private final UUID gameId;
    private final UUID teamId; // null for admin sessions
    
    public WebSocketSession(Session session, SessionType type, UUID gameId, UUID teamId) {
        this.session = session;
        this.type = type;
        this.gameId = gameId;
        this.teamId = teamId;
    }
    
    public static WebSocketSession forTeam(Session session, UUID gameId, UUID teamId) {
        return new WebSocketSession(session, SessionType.TEAM, gameId, teamId);
    }
    
    public static WebSocketSession forAdmin(Session session, UUID gameId) {
        return new WebSocketSession(session, SessionType.ADMIN, gameId, null);
    }
    
    public Session getSession() { return session; }
    public SessionType getType() { return type; }
    public UUID getGameId() { return gameId; }
    public UUID getTeamId() { return teamId; }
    
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
        // Check game targeting
        if (message.getTargetGameId() != null && !message.getTargetGameId().equals(gameId)) {
            return false;
        }
        
        // Check admin-only messages
        if (message.isAdminOnly() && !isAdmin()) {
            return false;
        }
        
        // Check team targeting (admin sessions receive all team messages for their game)
        if (message.getTargetTeamId() != null && !isAdmin()) {
            return message.getTargetTeamId().equals(teamId);
        }
        
        return true;
    }
}
