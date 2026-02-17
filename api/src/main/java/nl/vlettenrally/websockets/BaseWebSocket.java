package nl.vlettenrally.websockets;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.Session;
import nl.vlettenrally.websockets.dto.WebSocketResponse;

/**
 * Base class for WebSocket endpoints providing common functionality.
 */
public abstract class BaseWebSocket {
    
    @Inject
    protected ObjectMapper objectMapper;
    
    @Inject
    protected WebSocketSessionManager sessionManager;
    
    @Inject
    protected ValkeySessionStore valkeySessionStore;
    
    @Inject
    protected WebSocketPingHandler pingHandler;
    
    @OnClose
    public void onClose(Session session) {
        sessionManager.removeSession(session.getId());
        valkeySessionStore.removeSession(session.getId());
        Log.infof("WebSocket closed: %s", session.getId());
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        Log.errorf("WebSocket error on session %s: %s", session.getId(), throwable.getMessage());
        sessionManager.removeSession(session.getId());
        valkeySessionStore.removeSession(session.getId());
    }
    
    protected void ensureSessionRegistered(Session session) {
        String sessionId = session.getId();
        
        if (sessionManager.getSession(sessionId) != null) {
            return;
        }
        
        WebSocketSessionMetadata metadata = valkeySessionStore.getSession(sessionId);
        if (metadata != null) {
            WebSocketSession wsSession = createSessionFromMetadata(session, metadata);
            if (wsSession != null) {
                sessionManager.addSession(wsSession);
                Log.infof("Registered session %s from Valkey (cross-pod)", sessionId);
            }
        } else {
            Log.warnf("Session %s not found", sessionId);
        }
    }
    
    protected WebSocketSession createSessionFromMetadata(Session session, WebSocketSessionMetadata metadata) {
        if (metadata.isTeam()) {
            return WebSocketSession.forTeam(session, metadata.getGameId(), metadata.getTeamId());
        } else if (metadata.isAdmin()) {
            if (metadata.getGameId() != null) {
                return WebSocketSession.forAdmin(session, metadata.getGameId());
            } else {
                return WebSocketSession.forAdmin(session);
            }
        }
        return null;
    }
    
    protected void sendResponse(Session session, WebSocketResponse response) {
        try {
            session.getAsyncRemote().sendText(objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException e) {
            Log.errorf("Failed to serialize response for session %s: %s", session.getId(), e.getMessage());
        }
    }
}
