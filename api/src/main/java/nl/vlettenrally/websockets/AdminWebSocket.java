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
    WebSocketPingHandler pingHandler;
    
    @OnOpen
    @RolesAllowed("admin")
    public void onOpen(Session session) {
        // Store session metadata in Valkey for cross-pod access
        // Do NOT add to local session manager yet - wait for first message
        // This avoids issues when WebSocket connects to different pod than handshake
        WebSocketSessionMetadata metadata = WebSocketSessionMetadata.forAdminNoGame(session.getId());
        valkeySessionStore.storeSession(metadata);
        
        Log.infof("Admin WebSocket opened - Session: %s (stored in Valkey only, no game selected yet)", session.getId());
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
            
            // Ensure session is registered locally - check Valkey if not found
            ensureSessionRegistered(session);
            
            // Handle PING messages with cross-pod support
            if (type == WebSocketMessageType.PING) {
                pingHandler.handlePing(session);
                return;
            }
            
            // For other messages, require local session registration
            WebSocketSession wsSession = sessionManager.getSession(session.getId());
            if (wsSession == null) {
                Log.warnf("Session %s not found in local manager after registration attempt", session.getId());
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
     * Ensure session is registered in local session manager.
     * If not found locally, check Valkey and register if found there.
     * This handles cross-pod scenarios where WebSocket connects to different pod than handshake.
     */
    private void ensureSessionRegistered(Session session) {
        String sessionId = session.getId();
        
        // Check if already registered locally
        if (sessionManager.getSession(sessionId) != null) {
            return; // Already registered
        }
        
        // Not in local list - check Valkey
        WebSocketSessionMetadata metadata = valkeySessionStore.getSession(sessionId);
        if (metadata != null) {
            // Found in Valkey - register locally using utility
            WebSocketSession wsSession = createSessionFromMetadata(session, metadata);
            if (wsSession != null) {
                sessionManager.addSession(wsSession);
                Log.infof("Registered session %s locally from Valkey metadata (cross-pod scenario)", sessionId);
            }
        } else {
            Log.warnf("Session %s not found in local manager or Valkey", sessionId);
        }
    }
    
    /**
     * Create a WebSocketSession from metadata.
     * Shared utility to ensure consistent session creation.
     */
    private WebSocketSession createSessionFromMetadata(Session session, WebSocketSessionMetadata metadata) {
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
