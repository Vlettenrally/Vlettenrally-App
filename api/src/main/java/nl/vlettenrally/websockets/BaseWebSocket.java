package nl.vlettenrally.websockets;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.Session;
import nl.vlettenrally.websockets.dto.WebSocketResponse;

import java.time.Duration;

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

    private static final int MAX_LOOKUP_TIME_SECONDS = 2;
    
    @OnClose
    public void onClose(Session session) {
        sessionManager.removeSession(session.getId());
        valkeySessionStore.removeSession(session.getId()).subscribe().with(
            item -> {},
            failure -> Log.errorf("Error removing session from Valkey: %s", failure.getMessage())
        );
        Log.infof("WebSocket closed: %s", session.getId());
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        Log.errorf("WebSocket error on session %s: %s", session.getId(), throwable.getMessage());
        sessionManager.removeSession(session.getId());
        valkeySessionStore.removeSession(session.getId()).subscribe().with(
            item -> {},
            failure -> Log.errorf("Error removing session from Valkey: %s", failure.getMessage())
        );
    }
    
    protected void ensureSessionRegistered(Session session) {
        String sessionId = session.getId();
        
        if (sessionManager.getSession(sessionId) != null) {
            return;
        }
        
        // Block and wait for Valkey lookup to complete (cross-pod session recovery)
        try {
            WebSocketSessionMetadata metadata = valkeySessionStore.getSession(sessionId)
                .await().atMost(Duration.ofSeconds(MAX_LOOKUP_TIME_SECONDS));

            if (metadata != null) {
                WebSocketSession wsSession = createSessionFromMetadata(session, metadata);
                if (wsSession != null) {
                    sessionManager.addSession(wsSession);
                    Log.infof("Registered session %s from Valkey (cross-pod)", sessionId);
                }
            } else {
                Log.warnf("Session %s not found in Valkey", sessionId);
            }
        } catch (Exception e) {
            Log.errorf("Error getting session from Valkey: %s", e.getMessage());
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
