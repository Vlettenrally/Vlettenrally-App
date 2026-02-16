package nl.vlettenrally.websockets;

import java.util.Collection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.websocket.Session;

/**
 * Unit tests for WebSocketSessionManager.
 * Tests session registration, removal, and targeting/filtering logic.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketSessionManagerTest {

    WebSocketSessionManager manager;

    @Mock Session sessionA;
    @Mock Session sessionB;
    @Mock Session sessionC;
    @Mock Session sessionD;

    private final UUID gameId1 = UUID.randomUUID();
    private final UUID gameId2 = UUID.randomUUID();
    private final UUID teamId1 = UUID.randomUUID();
    private final UUID teamId2 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        manager = new WebSocketSessionManager();
        when(sessionA.getId()).thenReturn("session-a");
        when(sessionB.getId()).thenReturn("session-b");
        when(sessionC.getId()).thenReturn("session-c");
        when(sessionD.getId()).thenReturn("session-d");
    }

    @Test
    void addSession_andGetSession() {
        WebSocketSession ws = WebSocketSession.forTeam(sessionA, gameId1, teamId1);
        manager.addSession(ws);

        assertEquals(ws, manager.getSession("session-a"));
        assertEquals(1, manager.getSessionCount());
    }

    @Test
    void removeSession_removesCorrectly() {
        WebSocketSession ws = WebSocketSession.forTeam(sessionA, gameId1, teamId1);
        manager.addSession(ws);
        manager.removeSession("session-a");

        assertNull(manager.getSession("session-a"));
        assertEquals(0, manager.getSessionCount());
    }

    @Test
    void getSessionsByGame_filtersCorrectly() {
        manager.addSession(WebSocketSession.forTeam(sessionA, gameId1, teamId1));
        manager.addSession(WebSocketSession.forTeam(sessionB, gameId1, teamId2));
        manager.addSession(WebSocketSession.forTeam(sessionC, gameId2, teamId1));

        Collection<WebSocketSession> result = manager.getSessionsByGame(gameId1);
        assertEquals(2, result.size());
    }

    @Test
    void getTeamSessionsByGame_excludesAdmins() {
        manager.addSession(WebSocketSession.forTeam(sessionA, gameId1, teamId1));
        manager.addSession(WebSocketSession.forAdmin(sessionB, gameId1));
        manager.addSession(WebSocketSession.forTeam(sessionC, gameId1, teamId2));

        Collection<WebSocketSession> result = manager.getTeamSessionsByGame(gameId1);
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(WebSocketSession::isTeam));
    }

    @Test
    void getAdminSessionsByGame_excludesTeams() {
        manager.addSession(WebSocketSession.forTeam(sessionA, gameId1, teamId1));
        manager.addSession(WebSocketSession.forAdmin(sessionB, gameId1));
        manager.addSession(WebSocketSession.forAdmin(sessionC, gameId1));

        Collection<WebSocketSession> result = manager.getAdminSessionsByGame(gameId1);
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(WebSocketSession::isAdmin));
    }

    @Test
    void getSessionByTeam_findsCorrectTeam() {
        manager.addSession(WebSocketSession.forTeam(sessionA, gameId1, teamId1));
        manager.addSession(WebSocketSession.forTeam(sessionB, gameId1, teamId2));

        WebSocketSession result = manager.getSessionByTeam(teamId2);
        assertNotNull(result);
        assertEquals("session-b", result.getSessionId());
    }

    @Test
    void getSessionByTeam_returnsNullWhenNotFound() {
        manager.addSession(WebSocketSession.forTeam(sessionA, gameId1, teamId1));

        assertNull(manager.getSessionByTeam(UUID.randomUUID()));
    }

    @Test
    void getSessionCountByGame_countsCorrectly() {
        manager.addSession(WebSocketSession.forTeam(sessionA, gameId1, teamId1));
        manager.addSession(WebSocketSession.forTeam(sessionB, gameId1, teamId2));
        manager.addSession(WebSocketSession.forAdmin(sessionC, gameId1));
        manager.addSession(WebSocketSession.forTeam(sessionD, gameId2, teamId1));

        assertEquals(3, manager.getSessionCountByGame(gameId1));
        assertEquals(1, manager.getSessionCountByGame(gameId2));
    }

    // --- getTargetSessions routing tests ---

    @Test
    void getTargetSessions_gameBroadcast_reachesAllInGame() {
        manager.addSession(WebSocketSession.forTeam(sessionA, gameId1, teamId1));
        manager.addSession(WebSocketSession.forTeam(sessionB, gameId1, teamId2));
        manager.addSession(WebSocketSession.forAdmin(sessionC, gameId1));
        manager.addSession(WebSocketSession.forTeam(sessionD, gameId2, teamId1));

        WebSocketMessage message = new WebSocketMessage("RANKING_UPDATE", "data")
            .withTargetGame(gameId1);

        Collection<WebSocketSession> targets = manager.getTargetSessions(message);
        assertEquals(3, targets.size());
    }

    @Test
    void getTargetSessions_teamMessage_reachesTeamAndAdmins() {
        manager.addSession(WebSocketSession.forTeam(sessionA, gameId1, teamId1));
        manager.addSession(WebSocketSession.forTeam(sessionB, gameId1, teamId2));
        manager.addSession(WebSocketSession.forAdmin(sessionC, gameId1));

        WebSocketMessage message = new WebSocketMessage("message:new", "hello")
            .withTargetGame(gameId1)
            .withTargetTeam(teamId1);

        Collection<WebSocketSession> targets = manager.getTargetSessions(message);
        // Should include: teamId1's session + admin session (admins see all team messages)
        assertEquals(2, targets.size());
    }

    @Test
    void getTargetSessions_adminOnly_excludesTeams() {
        manager.addSession(WebSocketSession.forTeam(sessionA, gameId1, teamId1));
        manager.addSession(WebSocketSession.forTeam(sessionB, gameId1, teamId2));
        manager.addSession(WebSocketSession.forAdmin(sessionC, gameId1));

        WebSocketMessage message = new WebSocketMessage("ADMIN_ALERT", "alert")
            .withTargetGame(gameId1)
            .forAdminOnly();

        Collection<WebSocketSession> targets = manager.getTargetSessions(message);
        assertEquals(1, targets.size());
        assertTrue(targets.iterator().next().isAdmin());
    }

    @Test
    void getTargetSessions_globalBroadcast_reachesAllWithGame() {
        manager.addSession(WebSocketSession.forTeam(sessionA, gameId1, teamId1));
        manager.addSession(WebSocketSession.forTeam(sessionB, gameId2, teamId2));
        manager.addSession(WebSocketSession.forAdmin(sessionC, gameId1));
        // Admin with no game should NOT receive
        manager.addSession(WebSocketSession.forAdmin(sessionD));

        WebSocketMessage message = new WebSocketMessage("GLOBAL", "data");

        Collection<WebSocketSession> targets = manager.getTargetSessions(message);
        assertEquals(3, targets.size()); // all except admin with no game
    }

    @Test
    void getTargetSessions_emptyManager_returnsEmpty() {
        WebSocketMessage message = new WebSocketMessage("RANKING_UPDATE", "data")
            .withTargetGame(gameId1);

        Collection<WebSocketSession> targets = manager.getTargetSessions(message);
        assertTrue(targets.isEmpty());
    }
}
