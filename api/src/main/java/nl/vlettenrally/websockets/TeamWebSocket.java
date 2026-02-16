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
    
    @OnOpen
    @TeamScopedOrAdmin
    @RolesAllowed({"team", "admin"})
    public void onOpen(Session session, 
                       @PathParam("gameId") String gameId, 
                       @PathParam("teamId") String teamId) {
        try {
            UUID gameUuid = UUID.fromString(gameId);
            UUID teamUuid = UUID.fromString(teamId);
            
            WebSocketSession wsSession = WebSocketSession.forTeam(session, gameUuid, teamUuid);
            sessionManager.addSession(wsSession);
            
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

    @OnClose
    public void onClose(Session session) {
        sessionManager.removeSession(session.getId());
        Log.infof("Team WebSocket closed: %s", session.getId());
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        Log.errorf("Team WebSocket error on session %s: %s", session.getId(), throwable.getMessage());
        sessionManager.removeSession(session.getId());
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            WebSocketRequest request = objectMapper.readValue(message, WebSocketRequest.class);
            
            // Handle PING messages - respond with PONG immediately
            // Don't require session lookup to support multi-pod deployments
            if (request.getType() == WebSocketMessageType.PING) {
                // Try to update activity if session is registered locally
                WebSocketSession wsSession = sessionManager.getSession(session.getId());
                if (wsSession != null) {
                    wsSession.updateActivity();
                }
                // Always send PONG response regardless of local session registration
                sendResponse(session, WebSocketResponse.pong());
                return;
            }
            
            // For other message types, require session to be registered locally
            WebSocketSession wsSession = sessionManager.getSession(session.getId());
            if (wsSession == null) {
                return;
            }
            
            Log.infof("Team WebSocket message from Team %s: %s", wsSession.getTeamId(), message);
            
        } catch (JsonProcessingException e) {
            Log.errorf("Failed to parse WebSocket message: %s", e.getMessage());
            sendResponse(session, WebSocketResponse.error("Invalid message format"));
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
