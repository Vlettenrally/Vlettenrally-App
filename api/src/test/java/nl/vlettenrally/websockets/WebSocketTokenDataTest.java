package nl.vlettenrally.websockets;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for WebSocketTokenData.
 */
class WebSocketTokenDataTest {

    private final UUID adminId = UUID.randomUUID();
    private final UUID teamId = UUID.randomUUID();
    private final UUID gameId = UUID.randomUUID();

    @Test
    void forAdmin_createsCorrectData() {
        WebSocketTokenData data = WebSocketTokenData.forAdmin(adminId, "Test Admin");

        assertEquals("admin", data.role());
        assertEquals(adminId, data.userId());
        assertEquals("Test Admin", data.userName());
        assertNull(data.gameId());
        assertNull(data.teamId());
        assertNotNull(data.createdAt());
        assertTrue(data.createdAt().isBefore(Instant.now().plusSeconds(1)));
    }

    @Test
    void forTeam_createsCorrectData() {
        WebSocketTokenData data = WebSocketTokenData.forTeam(teamId, "Test Team", gameId);

        assertEquals("team", data.role());
        assertEquals(teamId, data.userId());
        assertEquals("Test Team", data.userName());
        assertEquals(gameId, data.gameId());
        assertEquals(teamId, data.teamId());
        assertNotNull(data.createdAt());
    }

    @Test
    void isAdmin_returnsTrueForAdminRole() {
        WebSocketTokenData adminData = WebSocketTokenData.forAdmin(adminId, "Admin");
        WebSocketTokenData teamData = WebSocketTokenData.forTeam(teamId, "Team", gameId);

        assertTrue(adminData.isAdmin());
        assertFalse(adminData.isTeam());

        assertFalse(teamData.isAdmin());
        assertTrue(teamData.isTeam());
    }

    @Test
    void isTeam_returnsTrueForTeamRole() {
        WebSocketTokenData teamData = WebSocketTokenData.forTeam(teamId, "Team", gameId);

        assertTrue(teamData.isTeam());
        assertFalse(teamData.isAdmin());
    }

    @Test
    void recordEquality_worksCorrectly() {
        Instant now = Instant.now();
        WebSocketTokenData data1 = new WebSocketTokenData("admin", adminId, "Admin", null, null, now);
        WebSocketTokenData data2 = new WebSocketTokenData("admin", adminId, "Admin", null, null, now);

        assertEquals(data1, data2);
        assertEquals(data1.hashCode(), data2.hashCode());
    }
}

