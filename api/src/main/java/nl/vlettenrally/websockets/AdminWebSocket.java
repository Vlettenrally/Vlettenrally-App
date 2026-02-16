package nl.vlettenrally.websockets;

import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import nl.vlettenrally.websockets.dto.WebSocketMessageType;
import nl.vlettenrally.websockets.dto.WebSocketRequest;
import nl.vlettenrally.websockets.dto.WebSocketResponse;

/**
 * WebSocket endpoint for admin connections
 * 
 * Admins connect to: /ws/admin
 * 
 * This is a game-agnostic endpoint. Admins must send a SWITCH_GAME message
 * to select which game's messages they want to receive.
 */
@ServerEndpoint("/ws/admin")
public class AdminWebSocket {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    @Inject
    WebSocketSessionManager sessionManager;
    
    @Inject
    ValkeySessionStore valkeySessionStore;
    
    @Inject
    ValkeyPubSubService valkeyPubSubService;
    
    @OnOpen
    @RolesAllowed("admin")
    public void onOpen(Session session) {
        // Create admin session without a game - must send SWITCH_GAME to receive messages
        WebSocketSession wsSession = WebSocketSession.forAdmin(session);
        sessionManager.addSession(wsSession);
        
        // Store session metadata in Valkey for cross-pod access
        WebSocketSessionMetadata metadata = WebSocketSessionMetadata.forAdminNoGame(session.getId());
        valkeySessionStore.storeSession(metadata);
        
        Log.infof("Admin WebSocket opened - Session: %s (no game selected yet)", session.getId());
    }

    @OnClose
    public void onClose(Session session) {
        sessionManager.removeSession(session.getId());
        valkeySessionStore.removeSession(session.getId());
        Log.infof("Admin WebSocket closed: %s", session.getId());
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        Log.errorf("Admin WebSocket error on session %s: %s", session.getId(), throwable.getMessage());
        sessionManager.removeSession(session.getId());
        valkeySessionStore.removeSession(session.getId());
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            WebSocketRequest request = objectMapper.readValue(message, WebSocketRequest.class);
            WebSocketMessageType type = request.getType();
            
            // Handle PING messages with cross-pod support
            if (type == WebSocketMessageType.PING) {
                handlePing(session);
                return;
            }
            
            // For other messages, require local session registration
            WebSocketSession wsSession = sessionManager.getSession(session.getId());
            if (wsSession == null) {
                return;
            }
            
            if (type == WebSocketMessageType.SWITCH_GAME) {
                handleSwitchGame(request, wsSession, session);
                return;
            }
            
            Log.infof("Admin WebSocket message from Game %s: %s", wsSession.getGameId(), message);
            
        } catch (JsonProcessingException e) {
            Log.errorf("Failed to parse WebSocket message: %s", e.getMessage());
            sendResponse(session, WebSocketResponse.error("Invalid message format"));
        }
    }
    
    /**
     * Handle PING messages with cross-pod support.
     * <p>
     * If session is registered locally, update activity and send PONG directly.
     * Otherwise, check Valkey for session metadata and publish PONG via pub/sub.
     */
    private void handlePing(Session session) {
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
    
    private void handleSwitchGame(WebSocketRequest request, WebSocketSession wsSession, Session session) {
        String gameIdStr = request.getGameId();
        
        if (gameIdStr == null || gameIdStr.isEmpty()) {
            sendResponse(session, WebSocketResponse.error("SWITCH_GAME requires a gameId"));
            return;
        }
        
        try {
            UUID gameId = UUID.fromString(gameIdStr);
            UUID previousGameId = wsSession.getGameId();
            wsSession.setGameId(gameId);
            
            // Update game ID in Valkey as well
            valkeySessionStore.updateGameId(session.getId(), gameId);
            
            Log.infof("Admin session %s switched from game %s to game %s", session.getId(), previousGameId, gameId);
            
            sendResponse(session, WebSocketResponse.gameSwitched(gameId.toString()));
            
        } catch (IllegalArgumentException e) {
            Log.errorf("Invalid gameId in SWITCH_GAME: %s", e.getMessage());
            sendResponse(session, WebSocketResponse.error("Invalid gameId format"));
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
