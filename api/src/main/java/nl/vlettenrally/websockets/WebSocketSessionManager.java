package nl.vlettenrally.websockets;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.Session;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages WebSocket sessions and provides methods for session lookup
 */
@ApplicationScoped
public class WebSocketSessionManager {
    
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    
    public void addSession(WebSocketSession wsSession) {
        sessions.put(wsSession.getSessionId(), wsSession);
    }
    
    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
    }
    
    public WebSocketSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }
    
    public Collection<WebSocketSession> getAllSessions() {
        return sessions.values();
    }
    
    /**
     * Get all sessions for a specific game
     */
    public Collection<WebSocketSession> getSessionsByGame(UUID gameId) {
        return sessions.values().stream()
            .filter(s -> s.getGameId().equals(gameId))
            .collect(Collectors.toList());
    }
    
    /**
     * Get all team sessions for a specific game
     */
    public Collection<WebSocketSession> getTeamSessionsByGame(UUID gameId) {
        return sessions.values().stream()
            .filter(s -> s.getGameId().equals(gameId) && s.isTeam())
            .collect(Collectors.toList());
    }
    
    /**
     * Get all admin sessions for a specific game
     */
    public Collection<WebSocketSession> getAdminSessionsByGame(UUID gameId) {
        return sessions.values().stream()
            .filter(s -> s.getGameId().equals(gameId) && s.isAdmin())
            .collect(Collectors.toList());
    }
    
    /**
     * Get session for a specific team
     */
    public WebSocketSession getSessionByTeam(UUID teamId) {
        return sessions.values().stream()
            .filter(s -> s.isTeam() && teamId.equals(s.getTeamId()))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Get all sessions that should receive a specific message
     */
    public Collection<WebSocketSession> getTargetSessions(WebSocketMessage message) {
        return sessions.values().stream()
            .filter(s -> s.shouldReceive(message))
            .collect(Collectors.toList());
    }
    
    public int getSessionCount() {
        return sessions.size();
    }
    
    public int getSessionCountByGame(UUID gameId) {
        return (int) sessions.values().stream()
            .filter(s -> s.getGameId().equals(gameId))
            .count();
    }
}
