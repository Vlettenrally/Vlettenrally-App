package nl.vlettenrally.websockets;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.websocket.Session;

/**
 * Unit tests for WebSocketSession.shouldReceive() routing logic.
 * This is critical for ensuring messages reach the correct WebSocket clients.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketSessionTest {

    @Mock
    Session mockSession;

    private final UUID gameId = UUID.randomUUID();
    private final UUID otherGameId = UUID.randomUUID();
    private final UUID teamId = UUID.randomUUID();
    private final UUID otherTeamId = UUID.randomUUID();

    // --- Team session routing ---

    @Test
    void teamSession_receivesGameBroadcast() {
        WebSocketSession session = WebSocketSession.forTeam(mockSession, gameId, teamId);
        WebSocketMessage message = new WebSocketMessage("RANKING_UPDATE", "data")
            .withTargetGame(gameId);

        assertTrue(session.shouldReceive(message));
    }

    @Test
    void teamSession_doesNotReceiveOtherGameBroadcast() {
        WebSocketSession session = WebSocketSession.forTeam(mockSession, gameId, teamId);
        WebSocketMessage message = new WebSocketMessage("RANKING_UPDATE", "data")
            .withTargetGame(otherGameId);

        assertFalse(session.shouldReceive(message));
    }

    @Test
    void teamSession_receivesOwnTeamMessage() {
        WebSocketSession session = WebSocketSession.forTeam(mockSession, gameId, teamId);
        WebSocketMessage message = new WebSocketMessage("message:new", "hello")
            .withTargetGame(gameId)
            .withTargetTeam(teamId);

        assertTrue(session.shouldReceive(message));
    }

    @Test
    void teamSession_doesNotReceiveOtherTeamMessage() {
        WebSocketSession session = WebSocketSession.forTeam(mockSession, gameId, teamId);
        WebSocketMessage message = new WebSocketMessage("message:new", "hello")
            .withTargetGame(gameId)
            .withTargetTeam(otherTeamId);

        assertFalse(session.shouldReceive(message));
    }

    @Test
    void teamSession_doesNotReceiveAdminOnlyMessage() {
        WebSocketSession session = WebSocketSession.forTeam(mockSession, gameId, teamId);
        WebSocketMessage message = new WebSocketMessage("ADMIN_ALERT", "alert")
            .withTargetGame(gameId)
            .forAdminOnly();

        assertFalse(session.shouldReceive(message));
    }

    @Test
    void teamSession_receivesUntargetedBroadcast() {
        WebSocketSession session = WebSocketSession.forTeam(mockSession, gameId, teamId);
        WebSocketMessage message = new WebSocketMessage("GLOBAL", "data");

        assertTrue(session.shouldReceive(message));
    }

    // --- Admin session routing ---

    @Test
    void adminSession_receivesGameBroadcast() {
        WebSocketSession session = WebSocketSession.forAdmin(mockSession, gameId);
        WebSocketMessage message = new WebSocketMessage("RANKING_UPDATE", "data")
            .withTargetGame(gameId);

        assertTrue(session.shouldReceive(message));
    }

    @Test
    void adminSession_doesNotReceiveOtherGameBroadcast() {
        WebSocketSession session = WebSocketSession.forAdmin(mockSession, gameId);
        WebSocketMessage message = new WebSocketMessage("RANKING_UPDATE", "data")
            .withTargetGame(otherGameId);

        assertFalse(session.shouldReceive(message));
    }

    @Test
    void adminSession_receivesAdminOnlyMessage() {
        WebSocketSession session = WebSocketSession.forAdmin(mockSession, gameId);
        WebSocketMessage message = new WebSocketMessage("ADMIN_ALERT", "alert")
            .withTargetGame(gameId)
            .forAdminOnly();

        assertTrue(session.shouldReceive(message));
    }

    @Test
    void adminSession_receivesTeamTargetedMessage() {
        // Admins should see all team messages for their game
        WebSocketSession session = WebSocketSession.forAdmin(mockSession, gameId);
        WebSocketMessage message = new WebSocketMessage("message:new", "hello")
            .withTargetGame(gameId)
            .withTargetTeam(teamId);

        assertTrue(session.shouldReceive(message));
    }

    @Test
    void adminSession_withNoGame_receivesNothing() {
        WebSocketSession session = WebSocketSession.forAdmin(mockSession);
        WebSocketMessage message = new WebSocketMessage("RANKING_UPDATE", "data")
            .withTargetGame(gameId);

        assertFalse(session.shouldReceive(message));
    }

    @Test
    void adminSession_withNoGame_doesNotReceiveUntargetedBroadcast() {
        WebSocketSession session = WebSocketSession.forAdmin(mockSession);
        WebSocketMessage message = new WebSocketMessage("GLOBAL", "data");

        assertFalse(session.shouldReceive(message));
    }

    @Test
    void adminSession_afterSwitchGame_receivesNewGameMessages() {
        WebSocketSession session = WebSocketSession.forAdmin(mockSession);
        session.setGameId(gameId);

        WebSocketMessage message = new WebSocketMessage("RANKING_UPDATE", "data")
            .withTargetGame(gameId);

        assertTrue(session.shouldReceive(message));
    }

    // --- Session metadata ---

    @Test
    void teamSession_hasCorrectMetadata() {
        when(mockSession.getId()).thenReturn("sess-123");
        WebSocketSession session = WebSocketSession.forTeam(mockSession, gameId, teamId);

        assertTrue(session.isTeam());
        assertFalse(session.isAdmin());
        assertEquals(gameId, session.getGameId());
        assertEquals(teamId, session.getTeamId());
        assertEquals("sess-123", session.getSessionId());
        assertNotNull(session.getLastActivity());
    }

    @Test
    void adminSession_hasCorrectMetadata() {
        when(mockSession.getId()).thenReturn("sess-456");
        WebSocketSession session = WebSocketSession.forAdmin(mockSession, gameId);

        assertTrue(session.isAdmin());
        assertFalse(session.isTeam());
        assertEquals(gameId, session.getGameId());
        assertNull(session.getTeamId());
        assertEquals("sess-456", session.getSessionId());
    }

    @Test
    void updateActivity_updatesTimestamp() throws InterruptedException {
        WebSocketSession session = WebSocketSession.forTeam(mockSession, gameId, teamId);
        var firstActivity = session.getLastActivity();

        Thread.sleep(10); // ensure time passes
        session.updateActivity();

        assertTrue(session.getLastActivity().isAfter(firstActivity));
    }

    @Test
    void setGameId_throwsForTeamSession() {
        WebSocketSession session = WebSocketSession.forTeam(mockSession, gameId, teamId);

        assertThrows(IllegalStateException.class, () -> session.setGameId(otherGameId));
    }

    @Test
    void setGameId_worksForAdminSession() {
        WebSocketSession session = WebSocketSession.forAdmin(mockSession, gameId);
        session.setGameId(otherGameId);

        assertEquals(otherGameId, session.getGameId());
    }
}
