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
     * Otherwise, check Valkey for session metadata and publish PONG via pub/sub.
     */
    public void handlePing(Session session) {
        String sessionId = session.getId();
        
        // Try local session first (most common case - same pod)
        WebSocketSession wsSession = sessionManager.getSession(sessionId);
        if (wsSession != null) {
            wsSession.updateActivity();
            valkeySessionStore.updateActivity(sessionId);
            sendResponse(session, WebSocketResponse.pong());
            return;
        }
        
        // Session not local - check Valkey (cross-pod scenario)
        WebSocketSessionMetadata metadata = valkeySessionStore.getSession(sessionId);
        if (metadata != null) {
            // Session exists in Valkey - update activity and publish PONG via pub/sub
            valkeySessionStore.updateActivity(sessionId);
            
            // Create PONG message targeted to this specific session
            WebSocketMessage pongMessage = new WebSocketMessage("PONG", null)
                .withTargetSession(sessionId);
            valkeyPubSubService.publish(pongMessage);
            
            Log.debugf("Handled PING for remote session %s via Valkey pub/sub", sessionId);
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
