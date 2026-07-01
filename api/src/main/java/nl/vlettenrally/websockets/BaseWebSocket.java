package nl.vlettenrally.websockets;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
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

    @Inject
    protected AdminTopicSubscriptionManager adminTopicSubscriptionManager;

    @OnClose
    public void onClose(Session session) {
        cleanupSubscriptions(session);
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
        cleanupSubscriptions(session);
        sessionManager.removeSession(session.getId());
        valkeySessionStore.removeSession(session.getId()).subscribe().with(
            item -> {},
            failure -> Log.errorf("Error removing session from Valkey: %s", failure.getMessage())
        );
    }

    /**
     * Remove any topic subscriptions (map, per-page streams, checkpoint) for this
     * session (admin sessions only).
     */
    private void cleanupSubscriptions(Session session) {
        WebSocketSession wsSession = sessionManager.getSession(session.getId());
        if (wsSession == null || !wsSession.isAdmin()) return;
        if (wsSession.getGameId() != null) {
            adminTopicSubscriptionManager.unsubscribeGameTopics(session.getId(), wsSession.getGameId());
        }
        if (wsSession.getCheckpointId() != null) {
            adminTopicSubscriptionManager.unsubscribe(session.getId(), wsSession.getCheckpointId(), AdminTopics.CHECKPOINT);
        }
    }
    
    /**
     * Ensures a session is registered locally, checking Valkey for cross-pod recovery if needed.
     * Returns a Uni that completes with the WebSocketSession (or null if not found).
     * Callers must subscribe to this Uni and handle the result asynchronously.
     */
    protected Uni<WebSocketSession> ensureSessionRegistered(Session session) {
        String sessionId = session.getId();
        
        WebSocketSession existing = sessionManager.getSession(sessionId);
        if (existing != null) {
            return Uni.createFrom().item(existing);
        }
        
        // Look up in Valkey for cross-pod session recovery
        return valkeySessionStore.getSession(sessionId)
            .map(metadata -> {
                if (metadata != null) {
                    WebSocketSession wsSession = createSessionFromMetadata(session, metadata);
                    if (wsSession != null) {
                        sessionManager.addSession(wsSession);
                        Log.infof("Registered session %s from Valkey (cross-pod)", sessionId);
                        return wsSession;
                    }
                }
                Log.warnf("Session %s not found in Valkey", sessionId);
                return null;
            })
            .onFailure().recoverWithItem(failure -> {
                Log.errorf("Error getting session from Valkey: %s", failure.getMessage());
                return null;
            });
    }
    
    protected WebSocketSession createSessionFromMetadata(Session session, WebSocketSessionMetadata metadata) {
        if (metadata.isTeam()) {
            return WebSocketSession.forTeam(session, metadata.gameId(), metadata.teamId());
        } else if (metadata.isAdmin()) {
            if (metadata.gameId() != null) {
                return WebSocketSession.forAdmin(session, metadata.gameId());
            } else {
                return WebSocketSession.forAdmin(session);
            }
            // Note: metadata.adminId() is preserved in Valkey but not propagated to the
            // local WebSocketSession — local code paths don't currently need it.
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
