package nl.vlettenrally.websockets;

import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import nl.vlettenrally.websockets.dto.WebSocketMessageType;
import nl.vlettenrally.websockets.dto.WebSocketRequest;
import nl.vlettenrally.websockets.dto.WebSocketResponse;

/**
 * WebSocket endpoint for admin connections.
 * Admins connect to: /ws/admin
 */
@ServerEndpoint("/ws/admin")
public class AdminWebSocket extends BaseWebSocket {
    
    @OnOpen
    @RolesAllowed("admin")
    public void onOpen(Session session) {
        WebSocketSessionMetadata metadata = WebSocketSessionMetadata.forAdminNoGame(session.getId());
        valkeySessionStore.storeSession(metadata).subscribe().with(
            item -> {},
            failure -> Log.errorf("Error storing session in Valkey: %s", failure.getMessage())
        );
        
        Log.infof("Admin WebSocket opened - Session: %s", session.getId());
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            WebSocketRequest request = objectMapper.readValue(message, WebSocketRequest.class);
            WebSocketMessageType type = request.getType();
            
            if (type == WebSocketMessageType.PING) {
                pingHandler.handlePing(session);
                return;
            }
            
            ensureSessionRegistered(session);
            
            WebSocketSession wsSession = sessionManager.getSession(session.getId());
            if (wsSession == null) {
                Log.warnf("Session %s not found after registration attempt", session.getId());
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
            valkeySessionStore.updateGameId(session.getId(), gameId).subscribe().with(
                item -> {},
                failure -> Log.errorf("Error updating game ID in Valkey: %s", failure.getMessage())
            );
            
            if (previousGameId != null) {
                Log.infof("Admin session %s switched from game %s to game %s", session.getId(), previousGameId, gameId);
            } else {
                Log.infof("Admin session %s selected game %s", session.getId(), gameId);
            }
            sendResponse(session, WebSocketResponse.gameSwitched(gameId.toString()));
            
        } catch (IllegalArgumentException e) {
            Log.errorf("Invalid gameId in SWITCH_GAME: %s", e.getMessage());
            sendResponse(session, WebSocketResponse.error("Invalid gameId format"));
        }
    }
}
