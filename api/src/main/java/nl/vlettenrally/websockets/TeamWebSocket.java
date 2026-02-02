package nl.vlettenrally.websockets;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Session;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import nl.vlettenrally.api.security.TeamScopedOrAdmin;
import nl.vlettenrally.service.AuthService;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.logging.Log;

import java.io.IOException;
import java.util.UUID;

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
    
    @Inject
    WebSocketSessionManager sessionManager;

    @Inject
    AuthService authService;
    
    @ConfigProperty(name = "vlettenrally.websocket.idle-timeout-seconds", defaultValue = "60")
    int idleTimeoutSeconds;
    
    @OnOpen
    @TeamScopedOrAdmin
    @RolesAllowed({"team", "admin"})
    public void onOpen(Session session, 
                       @PathParam("gameId") String gameId, 
                       @PathParam("teamId") String teamId) {
        try {
            UUID gameUuid = UUID.fromString(gameId);
            UUID teamUuid = UUID.fromString(teamId);
            
            // Set idle timeout for this session
            session.setMaxIdleTimeout(idleTimeoutSeconds * 1000L);
            
            WebSocketSession wsSession = WebSocketSession.forTeam(session, gameUuid, teamUuid);
            sessionManager.addSession(wsSession);
            
            Log.info("Team WebSocket opened - Game: " + gameId + ", Team: " + teamId + ", Session: " + session.getId());
        } catch (IllegalArgumentException e) {
            Log.error("Invalid UUID in WebSocket connection: " + e.getMessage());
            closeWithError(session, "Invalid game or team identifier");
        }
    }

    private void closeWithError(Session session, String reason) {
        try {
            session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, reason));
        } catch (IOException e) {
            Log.error("Error closing WebSocket session: " + e.getMessage());
        }
    }

    @OnClose
    public void onClose(Session session) {
        sessionManager.removeSession(session.getId());
        Log.info("Team WebSocket closed: " + session.getId());
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        Log.error("Team WebSocket error on session " + session.getId() + ": " + throwable.getMessage());
        sessionManager.removeSession(session.getId());
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        // Ignore heartbeat messages from client
        if (message != null && message.contains("HEARTBEAT")) {
            return;
        }
        
        WebSocketSession wsSession = sessionManager.getSession(session.getId());
        if (wsSession != null) {
            Log.info("Team WebSocket message from Team " + wsSession.getTeamId() + ": " + message);
        }
    }
}
