package nl.vlettenrally.websockets;

import java.io.IOException;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.websocket.CloseReason;
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
 * WebSocket endpoint for team connections.
 * Teams connect to: /ws/game/{gameId}/team/{teamId}?token={accessToken}
 */
@ServerEndpoint("/ws/game/{gameId}/team/{teamId}")
public class TeamWebSocket extends BaseWebSocket {
    
    @OnOpen
    @TeamScopedOrAdmin
    @RolesAllowed({"team", "admin"})
    public void onOpen(Session session, 
                       @PathParam("gameId") String gameId, 
                       @PathParam("teamId") String teamId) {
        try {
            UUID gameUuid = UUID.fromString(gameId);
            UUID teamUuid = UUID.fromString(teamId);
            
            WebSocketSessionMetadata metadata = WebSocketSessionMetadata.forTeam(session.getId(), gameUuid, teamUuid);
            valkeySessionStore.storeSession(metadata).subscribe().with(
                item -> {},
                failure -> Log.errorf("Error storing session in Valkey: %s", failure.getMessage())
            );
            
            Log.infof("Team WebSocket opened - Game: %s, Team: %s, Session: %s", gameId, teamId, session.getId());
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

    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            WebSocketRequest request = objectMapper.readValue(message, WebSocketRequest.class);
            
            if (request.getType() == WebSocketMessageType.PING) {
                pingHandler.handlePing(session);
                return;
            }
            
            ensureSessionRegistered(session);
            
            WebSocketSession wsSession = sessionManager.getSession(session.getId());
            if (wsSession == null) {
                Log.warnf("Session %s not found after registration attempt", session.getId());
                return;
            }
            
            Log.infof("Team WebSocket message from Team %s: %s", wsSession.getTeamId(), message);
            
        } catch (JsonProcessingException e) {
            Log.errorf("Failed to parse WebSocket message: %s", e.getMessage());
            sendResponse(session, WebSocketResponse.error("Invalid message format"));
        }
    }
}
