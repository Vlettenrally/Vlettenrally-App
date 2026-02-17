package nl.vlettenrally.websockets;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.Session;
import nl.vlettenrally.websockets.dto.WebSocketResponse;

/**
 * Utility class for handling WebSocket PING messages with cross-pod support.
 */
@ApplicationScoped
public class WebSocketPingHandler extends BaseWebSocket {
    
    /**
     * Handle PING messages with cross-pod support.
     * <p>
     * If session is registered locally, update activity and send PONG directly.
     * Otherwise, check Valkey for session metadata. If found, register locally and send PONG.
     * If not found in Valkey, log a warning that the session is unknown (likely expired or invalid).
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
            WebSocketSession newSession = createSessionFromMetadata(session, metadata);
            if (newSession != null) {
                sessionManager.addSession(newSession);
                newSession.updateActivity();
                valkeySessionStore.updateActivity(sessionId);
                sendResponse(session, WebSocketResponse.pong());
                
                Log.infof("Registered session %s locally from Valkey on PING (cross-pod scenario)", sessionId);
            }
        } else {
            // Session not found anywhere - likely expired or invalid
            Log.warnf("Received PING for unknown session: %s", sessionId);
        }
    }
}
