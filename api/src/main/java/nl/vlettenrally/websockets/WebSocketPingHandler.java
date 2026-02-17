package nl.vlettenrally.websockets;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.Session;
import nl.vlettenrally.websockets.dto.WebSocketResponse;

/**
 * Utility class for handling WebSocket PING messages with cross-pod support.
 */
@ApplicationScoped
public class WebSocketPingHandler {
    
    @Inject
    WebSocketSessionManager sessionManager;
    
    @Inject
    ValkeySessionStore valkeySessionStore;
    
    @Inject
    ValkeyPubSubService valkeyPubSubService;
    
    @Inject
    ObjectMapper objectMapper;
    
    /**
     * Handle PING messages with cross-pod support.
     * <p>
     * If session is registered locally, update activity and send PONG directly.
     * Otherwise, check Valkey for session metadata. If found, register locally and send PONG.
     * If still not found in Valkey, publish PONG via pub/sub (session may be on different pod).
     */
    public void handlePing(Session session) {
        String sessionId = session.getId();
        
        // Try local session first (most common case - same pod, already registered)
        WebSocketSession wsSession = sessionManager.getSession(sessionId);
        if (wsSession != null) {
            wsSession.updateActivity();
            valkeySessionStore.updateActivity(sessionId);
            sendResponse(session, WebSocketResponse.pong());
            return;
        }
        
        // Session not local - check Valkey (cross-pod scenario or first message)
        WebSocketSessionMetadata metadata = valkeySessionStore.getSession(sessionId);
        if (metadata != null) {
            // Found in Valkey - register locally and send PONG
            WebSocketSession newSession;
            if (metadata.isTeam()) {
                newSession = WebSocketSession.forTeam(session, metadata.getGameId(), metadata.getTeamId());
            } else if (metadata.getGameId() != null) {
                newSession = WebSocketSession.forAdmin(session, metadata.getGameId());
            } else {
                newSession = WebSocketSession.forAdmin(session);
            }
            sessionManager.addSession(newSession);
            newSession.updateActivity();
            valkeySessionStore.updateActivity(sessionId);
            sendResponse(session, WebSocketResponse.pong());
            
            Log.infof("Registered session %s locally from Valkey on PING (cross-pod scenario)", sessionId);
        } else {
            // Session not found anywhere - likely expired or invalid
            Log.warnf("Received PING for unknown session: %s", sessionId);
        }
    }
    
    private void sendResponse(Session session, WebSocketResponse response) {
        try {
            session.getAsyncRemote().sendText(objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException e) {
            Log.errorf("Failed to serialize response for session %s: %s", session.getId(), e.getMessage());
        }
    }
}
