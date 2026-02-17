package nl.vlettenrally.websockets;

import java.io.IOException;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.websocket.CloseReason;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import nl.vlettenrally.websockets.dto.WebSocketMessageType;
import nl.vlettenrally.websockets.dto.WebSocketRequest;
import nl.vlettenrally.websockets.dto.WebSocketResponse;

/**
 * WebSocket endpoint for team connections.
 * Teams connect to: /ws/team/{token}
 *
 * The token is obtained from GET /api/v1/ws/token/team/{gameId}/{teamId}
 */
@ServerEndpoint("/ws/team/{token}")
public class TeamWebSocket extends BaseWebSocket {
    
    @Inject
    WebSocketTokenService tokenService;

    @OnOpen
    public void onOpen(Session session, @PathParam("token") String token) {
        // Validate token and extract team data
        tokenService.validateAndConsumeToken(token).subscribe().with(
            tokenData -> {
                if (tokenData == null || !tokenData.isTeam()) {
                    Log.warnf("Invalid or non-team WebSocket token for session %s", session.getId());
                    closeWithError(session, "Invalid or expired token");
                    return;
                }

                UUID gameId = tokenData.gameId();
                UUID teamId = tokenData.teamId();

                // Register session locally for immediate availability
                WebSocketSession wsSession = WebSocketSession.forTeam(session, gameId, teamId);
                sessionManager.addSession(wsSession);

                // Store to Valkey for cross-pod session recovery
                WebSocketSessionMetadata metadata = WebSocketSessionMetadata.forTeam(session.getId(), gameId, teamId);
                valkeySessionStore.storeSession(metadata).subscribe().with(
                    item -> Log.debugf("Session %s stored in Valkey", session.getId()),
                    failure -> Log.errorf("Error storing session in Valkey: %s", failure.getMessage())
                );

                Log.infof("Team WebSocket opened - Game: %s, Team: %s, Session: %s, Name: %s",
                    gameId, teamId, session.getId(), tokenData.userName());
            },
            failure -> {
                Log.errorf("Error validating WebSocket token: %s", failure.getMessage());
                closeWithError(session, "Token validation failed");
            }
        );
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
            
            ensureSessionRegistered(session).subscribe().with(
                wsSession -> {
                    if (wsSession == null) {
                        Log.warnf("Session %s not found after registration attempt", session.getId());
                        return;
                    }
                    Log.infof("Team WebSocket message from Team %s: %s", wsSession.getTeamId(), message);
                },
                failure -> Log.errorf("Error processing message for session %s: %s", session.getId(), failure.getMessage())
            );

        } catch (JsonProcessingException e) {
            Log.errorf("Failed to parse WebSocket message: %s", e.getMessage());
            sendResponse(session, WebSocketResponse.error("Invalid message format"));
        }
    }
}
