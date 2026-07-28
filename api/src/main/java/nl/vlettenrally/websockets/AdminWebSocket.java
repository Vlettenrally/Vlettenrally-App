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
import nl.vlettenrally.observability.WebSocketMetrics.OpenOutcome;
import nl.vlettenrally.observability.WebSocketMetrics.SessionKind;
import nl.vlettenrally.websockets.dto.WebSocketMessageType;
import nl.vlettenrally.websockets.dto.WebSocketRequest;
import nl.vlettenrally.websockets.dto.WebSocketResponse;

/**
 * WebSocket endpoint for admin connections.
 * Admins connect to: /ws/admin/{token}
 * The token is obtained from GET /api/v1/ws/token/admin
 */
@ServerEndpoint("/ws/admin/{token}")
public class AdminWebSocket extends BaseWebSocket {
    
    @Inject
    WebSocketTokenService tokenService;

    @Inject
    AdminTopicSubscriptionManager adminTopicSubscriptionManager;

    @Inject
    CheckpointFcmSubscriptionManager checkpointFcmSubscriptionManager;

    @OnOpen
    public void onOpen(Session session, @PathParam("token") String token) {
        // Validate token and extract admin data
        tokenService.validateAndConsumeToken(token).subscribe().with(
            tokenData -> {
                if (tokenData == null || !tokenData.isAdmin()) {
                    Log.warnf("Invalid or non-admin WebSocket token for session %s", session.getId());
                    metrics.sessionOpened(SessionKind.ADMIN, OpenOutcome.INVALID_TOKEN);
                    closeWithError(session, "Invalid or expired token");
                    return;
                }

                // Register session locally for immediate availability
                WebSocketSession wsSession = WebSocketSession.forAdmin(session);
                sessionManager.addSession(wsSession);

                // Store to Valkey for cross-pod session recovery
                WebSocketSessionMetadata metadata = WebSocketSessionMetadata.forAdminNoGame(session.getId(), tokenData.userId());
                valkeySessionStore.storeSession(metadata).subscribe().with(
                    item -> Log.debugf("Session %s stored in Valkey", session.getId()),
                    failure -> Log.errorf("Error storing session in Valkey: %s", failure.getMessage())
                );

                metrics.sessionOpened(SessionKind.ADMIN, OpenOutcome.OK);
                Log.infof("Admin WebSocket opened - Session: %s, Admin: %s", session.getId(), tokenData.userName());
            },
            failure -> {
                Log.errorf("Error validating WebSocket token: %s", failure.getMessage());
                metrics.sessionOpened(SessionKind.ADMIN, OpenOutcome.VALIDATION_ERROR);
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
            WebSocketMessageType type = request.getType();
            
            if (type == WebSocketMessageType.PING) {
                pingHandler.handlePing(session);
                return;
            }
            
            ensureSessionRegistered(session).subscribe().with(
                wsSession -> {
                    if (wsSession == null) {
                        Log.warnf("Session %s not found after registration attempt", session.getId());
                        return;
                    }

                    if (type == WebSocketMessageType.SWITCH_GAME) {
                        handleSwitchGame(request, wsSession, session);
                        return;
                    }

                    if (type == WebSocketMessageType.ADMIN_HEARTBEAT) {
                        // Covers the map (topic "map") and every per-page admin stream.
                        adminTopicSubscriptionManager.subscribe(session.getId(), wsSession.getGameId(), request.getTopic());
                        return;
                    }

                    if (type == WebSocketMessageType.CHECKPOINT_HEARTBEAT) {
                        handleCheckpointHeartbeat(request, wsSession, session);
                        return;
                    }

                    if (type == WebSocketMessageType.CHECKPOINT_SUBSCRIBE) {
                        handleCheckpointFcmSubscribe(request, session, true);
                        return;
                    }

                    if (type == WebSocketMessageType.CHECKPOINT_UNSUBSCRIBE) {
                        handleCheckpointFcmSubscribe(request, session, false);
                        return;
                    }

                    Log.infof("Admin WebSocket message from Game %s: %s", wsSession.getGameId(), message);
                },
                failure -> Log.errorf("Error processing message for session %s: %s", session.getId(), failure.getMessage())
            );

        } catch (JsonProcessingException e) {
            Log.errorf("Failed to parse WebSocket message: %s", e.getMessage());
            sendResponse(session, WebSocketResponse.error("Invalid message format"));
        }
    }
    
    private void handleCheckpointFcmSubscribe(WebSocketRequest request, Session session, boolean subscribe) {
        String checkpointIdStr = request.getCheckpointId();
        if (checkpointIdStr == null || checkpointIdStr.isEmpty()) {
            sendResponse(session, WebSocketResponse.error("checkpointId is required"));
            return;
        }
        UUID checkpointId;
        try {
            checkpointId = UUID.fromString(checkpointIdStr);
        } catch (IllegalArgumentException e) {
            sendResponse(session, WebSocketResponse.error("Invalid checkpointId format"));
            return;
        }

        valkeySessionStore.getSession(session.getId()).subscribe().with(
            metadata -> {
                if (metadata == null || metadata.adminId() == null) {
                    Log.warnf("No admin metadata for session %s — cannot %s FCM checkpoint subscription",
                        session.getId(), subscribe ? "add" : "remove");
                    return;
                }
                if (subscribe) {
                    checkpointFcmSubscriptionManager.subscribe(checkpointId, metadata.adminId());
                } else {
                    checkpointFcmSubscriptionManager.unsubscribe(checkpointId, metadata.adminId());
                }
            },
            failure -> Log.errorf("Failed to load session metadata for FCM (un)subscribe: %s", failure.getMessage())
        );
    }

    private void handleCheckpointHeartbeat(WebSocketRequest request, WebSocketSession wsSession, Session session) {
        String checkpointIdStr = request.getCheckpointId();
        UUID newCheckpointId = null;
        if (checkpointIdStr != null && !checkpointIdStr.isEmpty()) {
            try {
                newCheckpointId = UUID.fromString(checkpointIdStr);
            } catch (IllegalArgumentException e) {
                Log.warnf("Invalid checkpointId in CHECKPOINT_HEARTBEAT: %s", checkpointIdStr);
                sendResponse(session, WebSocketResponse.error("Invalid checkpointId format"));
                return;
            }
        }

        UUID previous = wsSession.getCheckpointId();
        if (previous != null && !previous.equals(newCheckpointId)) {
            // Operator switched (or cleared) their checkpoint — drop the old subscription
            adminTopicSubscriptionManager.unsubscribe(session.getId(), previous, AdminTopics.CHECKPOINT);
        }

        wsSession.setCheckpointId(newCheckpointId);

        if (newCheckpointId != null) {
            adminTopicSubscriptionManager.subscribe(session.getId(), newCheckpointId, AdminTopics.CHECKPOINT);
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

            // Drop every game-scoped subscription (map + per-page streams) on the old game
            if (previousGameId != null) {
                adminTopicSubscriptionManager.unsubscribeGameTopics(session.getId(), previousGameId);
            }

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
