package nl.vlettenrally.websockets;

import java.io.IOException;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import nl.vlettenrally.api.security.TeamScopedOrAdmin;
import nl.vlettenrally.websockets.dto.WebSocketMessageType;
import nl.vlettenrally.websockets.dto.WebSocketRequest;
import nl.vlettenrally.websockets.dto.WebSocketResponse;

/**
 * WebSocket endpoint for team connections
 * 
 * Teams connect to: /ws/game/{gameId}/team/{teamId}?token={accessToken}
 * 
 * Authentication is required via the token query parameter.
 * The token must be valid and the subject must match the teamId in the path.
 */
@ServerEndpoint("/ws/game/{gameId}/team/{teamId}")
public class TeamWebSocket {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    @Inject
    WebSocketSessionManager sessionManager;
    
    @Inject
    ValkeySessionStore valkeySessionStore;
    
    @Inject
    WebSocketPingHandler pingHandler;
    
    @OnOpen
    @TeamScopedOrAdmin
    @RolesAllowed({"team", "admin"})
    public void onOpen(Session session, 
                       @PathParam("gameId") String gameId, 
                       @PathParam("teamId") String teamId) {
        try {
            UUID gameUuid = UUID.fromString(gameId);
            UUID teamUuid = UUID.fromString(teamId);
            
            // Store session metadata in Valkey for cross-pod access
            // Do NOT add to local session manager yet - wait for first message
            // This avoids issues when WebSocket connects to different pod than handshake
            WebSocketSessionMetadata metadata = WebSocketSessionMetadata.forTeam(session.getId(), gameUuid, teamUuid);
            valkeySessionStore.storeSession(metadata);
            
            Log.infof("Team WebSocket opened - Game: %s, Team: %s, Session: %s (stored in Valkey only)", gameId, teamId, session.getId());
        } catch (IllegalArgumentException e) {
            Log.errorf("Invalid UUID in WebSocket connection: %s", e.getMessage());
            closeWithError(session, "Invalid game or team identifier");
        }
    }

    private void closeWithError(Session session, String reason) {
        try {
            session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, reason));
        } catch (IOException e) {
            Log.errorf("Error closing WebSocket session: %s", e.getMessage());
        }
    }

    @OnClose
    public void onClose(Session session) {
        sessionManager.removeSession(session.getId());
        valkeySessionStore.removeSession(session.getId());
        Log.infof("Team WebSocket closed: %s", session.getId());
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        Log.errorf("Team WebSocket error on session %s: %s", session.getId(), throwable.getMessage());
        sessionManager.removeSession(session.getId());
        valkeySessionStore.removeSession(session.getId());
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            WebSocketRequest request = objectMapper.readValue(message, WebSocketRequest.class);
            
            // Ensure session is registered locally - check Valkey if not found
            ensureSessionRegistered(session);
            
            // Handle PING messages with cross-pod support
            if (request.getType() == WebSocketMessageType.PING) {
                pingHandler.handlePing(session);
                return;
            }
            
            // For other messages, require local session registration
            WebSocketSession wsSession = sessionManager.getSession(session.getId());
            if (wsSession == null) {
                Log.warnf("Session %s not found in local manager after registration attempt", session.getId());
                return;
            }
            
            Log.infof("Team WebSocket message from Team %s: %s", wsSession.getTeamId(), message);
            
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
    
    private void sendResponse(Session session, WebSocketResponse response) {
        try {
            session.getAsyncRemote().sendText(objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException e) {
            Log.errorf("Failed to serialize response for session %s: %s", session.getId(), e.getMessage());
        }
    }
}
