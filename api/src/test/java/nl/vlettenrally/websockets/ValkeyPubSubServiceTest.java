package nl.vlettenrally.websockets;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import nl.vlettenrally.observability.WebSocketMetrics;

/**
 * Unit tests for ValkeyPubSubService.deliverLocally().
 * Tests that JSON messages from Valkey are correctly routed to local WebSocket sessions.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ValkeyPubSubServiceTest {

    @Spy
    WebSocketSessionManager sessionManager = new WebSocketSessionManager();

    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    WebSocketMetrics metrics;

    @InjectMocks
    ValkeyPubSubService service;
    private final UUID gameId = UUID.randomUUID();
    private final UUID teamId1 = UUID.randomUUID();
    private final UUID teamId2 = UUID.randomUUID();

    private Session mockSessionTeam1;
    private Session mockSessionTeam2;
    private Session mockSessionAdmin;
    private RemoteEndpoint.Async asyncTeam1;
    private RemoteEndpoint.Async asyncTeam2;
    private RemoteEndpoint.Async asyncAdmin;

    @BeforeEach
    void setUp() {
        // Set up mock sessions with async remotes
        mockSessionTeam1 = createMockSession("team1-session");
        asyncTeam1 = mockSessionTeam1.getAsyncRemote();

        mockSessionTeam2 = createMockSession("team2-session");
        asyncTeam2 = mockSessionTeam2.getAsyncRemote();

        mockSessionAdmin = createMockSession("admin-session");
        asyncAdmin = mockSessionAdmin.getAsyncRemote();

        // Register sessions
        sessionManager.addSession(WebSocketSession.forTeam(mockSessionTeam1, gameId, teamId1));
        sessionManager.addSession(WebSocketSession.forTeam(mockSessionTeam2, gameId, teamId2));
        sessionManager.addSession(WebSocketSession.forAdmin(mockSessionAdmin, gameId));
    }

    private Session createMockSession(String id) {
        Session session = mock(Session.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        RemoteEndpoint.Async async = mock(RemoteEndpoint.Async.class);
        when(session.getAsyncRemote()).thenReturn(async);
        return session;
    }

    // --- Game broadcast ---

    @Test
    void deliverLocally_gameBroadcast_reachesAllSessionsInGame() throws JsonProcessingException {
        WebSocketMessage msg = new WebSocketMessage("RANKING_UPDATE", "data")
            .withTargetGame(gameId);
        String json = objectMapper.writeValueAsString(msg);

        service.deliverLocally(json);

        verify(asyncTeam1).sendText(eq(json), any());
        verify(asyncTeam2).sendText(eq(json), any());
        verify(asyncAdmin).sendText(eq(json), any());
    }

    @Test
    void deliverLocally_gameBroadcast_doesNotReachOtherGame() throws JsonProcessingException {
        UUID otherGameId = UUID.randomUUID();
        WebSocketMessage msg = new WebSocketMessage("RANKING_UPDATE", "data")
            .withTargetGame(otherGameId);
        String json = objectMapper.writeValueAsString(msg);

        service.deliverLocally(json);

        verify(asyncTeam1, never()).sendText(anyString(), any());
        verify(asyncTeam2, never()).sendText(anyString(), any());
        verify(asyncAdmin, never()).sendText(anyString(), any());
    }

    // --- Team-targeted messages ---

    @Test
    void deliverLocally_teamMessage_reachesTargetTeamAndAdmin() throws JsonProcessingException {
        WebSocketMessage msg = new WebSocketMessage("message:new", "hello")
            .withTargetGame(gameId)
            .withTargetTeam(teamId1);
        String json = objectMapper.writeValueAsString(msg);

        service.deliverLocally(json);

        verify(asyncTeam1).sendText(eq(json), any());
        verify(asyncTeam2, never()).sendText(anyString(), any());
        verify(asyncAdmin).sendText(eq(json), any()); // admin sees all team msgs
    }

    @Test
    void deliverLocally_teamMessage_otherTeamDoesNotReceive() throws JsonProcessingException {
        WebSocketMessage msg = new WebSocketMessage("message:new", "hello")
            .withTargetGame(gameId)
            .withTargetTeam(teamId2);
        String json = objectMapper.writeValueAsString(msg);

        service.deliverLocally(json);

        verify(asyncTeam1, never()).sendText(anyString(), any());
        verify(asyncTeam2).sendText(eq(json), any());
        verify(asyncAdmin).sendText(eq(json), any());
    }

    // --- Admin-only messages ---

    @Test
    void deliverLocally_adminOnly_excludesTeams() throws JsonProcessingException {
        WebSocketMessage msg = new WebSocketMessage("ADMIN_ALERT", "alert")
            .withTargetGame(gameId)
            .forAdminOnly();
        String json = objectMapper.writeValueAsString(msg);

        service.deliverLocally(json);

        verify(asyncTeam1, never()).sendText(anyString(), any());
        verify(asyncTeam2, never()).sendText(anyString(), any());
        verify(asyncAdmin).sendText(eq(json), any());
    }

    // --- Edge cases ---

    @Test
    void deliverLocally_closedSession_isSkipped() throws JsonProcessingException {
        when(mockSessionTeam1.isOpen()).thenReturn(false);

        WebSocketMessage msg = new WebSocketMessage("RANKING_UPDATE", "data")
            .withTargetGame(gameId);
        String json = objectMapper.writeValueAsString(msg);

        service.deliverLocally(json);

        verify(asyncTeam1, never()).sendText(anyString(), any());
        verify(asyncTeam2).sendText(eq(json), any());
        verify(asyncAdmin).sendText(eq(json), any());
    }

    @Test
    void deliverLocally_invalidJson_doesNotThrow() {
        assertDoesNotThrow(() -> service.deliverLocally("not valid json {{{"));
    }

    @Test
    void deliverLocally_noSessions_doesNotThrow() throws JsonProcessingException {
        // Remove all sessions
        sessionManager.removeSession("team1-session");
        sessionManager.removeSession("team2-session");
        sessionManager.removeSession("admin-session");

        WebSocketMessage msg = new WebSocketMessage("RANKING_UPDATE", "data")
            .withTargetGame(gameId);
        String json = objectMapper.writeValueAsString(msg);

        assertDoesNotThrow(() -> service.deliverLocally(json));
    }

    @Test
    void deliverLocally_messageWithNullTargets_treatedAsGlobalBroadcast() throws JsonProcessingException {
        WebSocketMessage msg = new WebSocketMessage("GLOBAL", "data");
        String json = objectMapper.writeValueAsString(msg);

        service.deliverLocally(json);

        // All sessions with a gameId should receive (admin with no game would not)
        verify(asyncTeam1).sendText(eq(json), any());
        verify(asyncTeam2).sendText(eq(json), any());
        verify(asyncAdmin).sendText(eq(json), any());
    }

    @Test
    void deliverLocally_preservesOriginalJson() throws JsonProcessingException {
        // Ensure the exact JSON string from Valkey is forwarded, not re-serialized
        String originalJson = "{\"type\":\"RANKING_UPDATE\",\"payload\":\"data\",\"timestamp\":\"2026-01-01T00:00:00Z\",\"targetGameId\":\"" + gameId + "\",\"targetTeamId\":null,\"adminOnly\":false}";

        service.deliverLocally(originalJson);

        // Verify the exact original JSON is sent, not a re-serialized version
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(asyncTeam1).sendText(captor.capture(), any());
        assertEquals(originalJson, captor.getValue());
    }

    @Test
    void deliverLocally_adminWithNoGame_doesNotReceive() throws JsonProcessingException {
        // Add an admin with no game set
        Session noGameAdmin = createMockSession("no-game-admin");
        RemoteEndpoint.Async noGameAsync = noGameAdmin.getAsyncRemote();
        sessionManager.addSession(WebSocketSession.forAdmin(noGameAdmin));

        WebSocketMessage msg = new WebSocketMessage("RANKING_UPDATE", "data")
            .withTargetGame(gameId);
        String json = objectMapper.writeValueAsString(msg);

        service.deliverLocally(json);

        verify(noGameAsync, never()).sendText(anyString(), any());
    }

    // --- Session-targeted messages ---

    @Test
    void deliverLocally_targetSessionId_reachesOnlyThatSession() throws JsonProcessingException {
        WebSocketMessage msg = new WebSocketMessage("PONG", null)
            .withTargetSession("team1-session");
        String json = objectMapper.writeValueAsString(msg);

        service.deliverLocally(json);

        verify(asyncTeam1).sendText(eq(json), any());
        verify(asyncTeam2, never()).sendText(anyString(), any());
        verify(asyncAdmin, never()).sendText(anyString(), any());
    }

    @Test
    void deliverLocally_targetSessionId_unknownSession_doesNotThrow() throws JsonProcessingException {
        WebSocketMessage msg = new WebSocketMessage("PONG", null)
            .withTargetSession("unknown-session-id");
        String json = objectMapper.writeValueAsString(msg);

        assertDoesNotThrow(() -> service.deliverLocally(json));

        verify(asyncTeam1, never()).sendText(anyString(), any());
        verify(asyncTeam2, never()).sendText(anyString(), any());
        verify(asyncAdmin, never()).sendText(anyString(), any());
    }

    @Test
    void deliverLocally_targetSessionId_notBroadcast() throws JsonProcessingException {
        // Session-targeted message should not be broadcast even if it has targetGameId
        WebSocketMessage msg = new WebSocketMessage("PONG", null)
            .withTargetGame(gameId)
            .withTargetSession("team1-session");
        String json = objectMapper.writeValueAsString(msg);

        service.deliverLocally(json);

        // Only team1 should receive, not other sessions in same game
        verify(asyncTeam1).sendText(eq(json), any());
        verify(asyncTeam2, never()).sendText(anyString(), any());
        verify(asyncAdmin, never()).sendText(anyString(), any());
    }

    // --- Multi-session targeting (one publish, N recipients) ---

    @Test
    void deliverLocally_targetSessionIds_reachesEveryListedSession() throws JsonProcessingException {
        WebSocketMessage msg = new WebSocketMessage("PICTURE_PENDING", "data")
            .withTargetGame(gameId)
            .forAdminOnly()
            .withTargetSessions(java.util.List.of("team1-session", "admin-session"));
        String json = objectMapper.writeValueAsString(msg);

        service.deliverLocally(json);

        verify(asyncTeam1).sendText(anyString(), any());
        verify(asyncAdmin).sendText(anyString(), any());
        verify(asyncTeam2, never()).sendText(anyString(), any());
    }

    @Test
    void deliverLocally_targetSessionIds_stripsTheRoutingFieldFromTheFrame() throws JsonProcessingException {
        // Leaving the list in would hand every recipient the other recipients' session ids,
        // which WebSocketPingHandler treats as adoptable.
        WebSocketMessage msg = new WebSocketMessage("PICTURE_PENDING", "data")
            .withTargetGame(gameId)
            .forAdminOnly()
            .withTargetSessions(java.util.List.of("admin-session"));

        service.deliverLocally(objectMapper.writeValueAsString(msg));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(asyncAdmin).sendText(captor.capture(), any());
        assertFalse(captor.getValue().contains("targetSessionIds"));
        assertFalse(captor.getValue().contains("admin-session"));
        assertTrue(captor.getValue().contains("PICTURE_PENDING"));
    }

    @Test
    void deliverLocally_targetSessionIds_unknownSession_doesNotThrow() throws JsonProcessingException {
        WebSocketMessage msg = new WebSocketMessage("PICTURE_PENDING", "data")
            .withTargetSessions(java.util.List.of("nobody-here"));
        String json = objectMapper.writeValueAsString(msg);

        assertDoesNotThrow(() -> service.deliverLocally(json));
        verify(asyncTeam1, never()).sendText(anyString(), any());
    }

    // --- Multiplexed envelopes (one publish, per-team payloads) ---

    @Test
    void deliverLocally_multiplexed_deliversOnlyEachTeamsOwnSlice() throws Exception {
        WebSocketMessage msg = WebSocketMessage.multiplexed("RIVAL_UPDATE", gameId, java.util.Map.of(
            teamId1, java.util.Map.of("points", 10),
            teamId2, java.util.Map.of("points", 20)));

        service.deliverLocally(objectMapper.writeValueAsString(msg));

        ArgumentCaptor<String> team1 = ArgumentCaptor.forClass(String.class);
        verify(asyncTeam1).sendText(team1.capture(), any());
        assertEquals(10, objectMapper.readTree(team1.getValue()).get("payload").get("points").asInt());

        ArgumentCaptor<String> team2 = ArgumentCaptor.forClass(String.class);
        verify(asyncTeam2).sendText(team2.capture(), any());
        assertEquals(20, objectMapper.readTree(team2.getValue()).get("payload").get("points").asInt());
    }

    @Test
    void deliverLocally_multiplexed_neverLeaksTheWholeEnvelope() throws Exception {
        // The envelope holds every team's view; forwarding it verbatim would expose exactly
        // what the per-team rival payload deliberately withholds.
        WebSocketMessage msg = WebSocketMessage.multiplexed("RIVAL_UPDATE", gameId, java.util.Map.of(
            teamId1, java.util.Map.of("secret", "team1-only"),
            teamId2, java.util.Map.of("secret", "team2-only")));

        service.deliverLocally(objectMapper.writeValueAsString(msg));

        ArgumentCaptor<String> team1 = ArgumentCaptor.forClass(String.class);
        verify(asyncTeam1).sendText(team1.capture(), any());
        assertFalse(team1.getValue().contains("perTeamPayloads"));
        assertFalse(team1.getValue().contains("team2-only"));
        assertTrue(team1.getValue().contains("team1-only"));
    }

    @Test
    void deliverLocally_multiplexed_doesNotReachAdminSessions() throws Exception {
        WebSocketMessage msg = WebSocketMessage.multiplexed("RIVAL_UPDATE", gameId,
            java.util.Map.of(teamId1, java.util.Map.of("points", 10)));

        service.deliverLocally(objectMapper.writeValueAsString(msg));

        verify(asyncAdmin, never()).sendText(anyString(), any());
    }

    @Test
    void deliverLocally_multiplexed_skipsTeamsWithNoSlice() throws Exception {
        WebSocketMessage msg = WebSocketMessage.multiplexed("RIVAL_UPDATE", gameId,
            java.util.Map.of(teamId1, java.util.Map.of("points", 10)));

        service.deliverLocally(objectMapper.writeValueAsString(msg));

        verify(asyncTeam1).sendText(anyString(), any());
        verify(asyncTeam2, never()).sendText(anyString(), any());
    }

    @Test
    void deliverLocally_multiplexed_doesNotReachAnotherGame() throws Exception {
        WebSocketMessage msg = WebSocketMessage.multiplexed("RIVAL_UPDATE", UUID.randomUUID(),
            java.util.Map.of(teamId1, java.util.Map.of("points", 10)));

        service.deliverLocally(objectMapper.writeValueAsString(msg));

        verify(asyncTeam1, never()).sendText(anyString(), any());
        verify(asyncTeam2, never()).sendText(anyString(), any());
    }

    @Test
    void deliverLocally_multiplexed_isInertOnAPodThatOnlyUnderstandsLegacyRouting() throws Exception {
        // A rolling deploy leaves old pods in the pool. They ignore perTeamPayloads and fall
        // through to normal routing, so the envelope carries a sentinel targetTeamId that no
        // real team has — making it undeliverable there rather than a leak.
        WebSocketMessage msg = WebSocketMessage.multiplexed("RIVAL_UPDATE", gameId,
            java.util.Map.of(teamId1, java.util.Map.of("points", 10)));
        String json = objectMapper.writeValueAsString(msg);

        // Simulate the old code path: routing metadata only, no perTeamPayloads awareness.
        WebSocketMessage routing = objectMapper.readValue(json, WebSocketMessage.class);
        for (WebSocketSession s : sessionManager.getAllSessions()) {
            assertFalse(s.shouldReceive(routing),
                "multiplexed envelope must not be deliverable via legacy routing");
        }
    }
}
