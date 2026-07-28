package nl.vlettenrally.websockets;

import nl.vlettenrally.data.dto.game.TeamLocationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LocationBroadcastBuffer}. Both flush paths must collapse a whole
 * polling sweep into a single publish: the admin map into one session-list message, and the
 * teams' own locations into one multiplexed envelope per game.
 */
@ExtendWith(MockitoExtension.class)
class LocationBroadcastBufferTest {

    @Mock WebSocketPublisher webSocketPublisher;
    @Mock AdminTopicSubscriptionManager adminTopicSubscriptionManager;
    @InjectMocks LocationBroadcastBuffer buffer;

    // --- team location path ---

    @Test
    void flush_sendsOneMultiplexedEnvelopePerGame_regardlessOfTeamCount() {
        UUID gameId = UUID.randomUUID();
        UUID teamA = UUID.randomUUID();
        UUID teamB = UUID.randomUUID();
        buffer.bufferLocation(gameId, location(teamA, 52.1));
        buffer.bufferLocation(gameId, location(teamB, 52.2));

        buffer.flush();

        WebSocketMessage sent = capturePublished();
        assertEquals(WebSocketEventTypes.LOCATION_UPDATE, sent.getType());
        assertEquals(gameId, sent.getTargetGameId());
        assertTrue(sent.isTeamOnly());
        assertFalse(sent.isAdminOnly());
        assertEquals(Set.of(teamA, teamB), sent.getPerTeamPayloads().keySet());
    }

    @Test
    void bufferLocation_keepsOnlyTheLatestFixPerTeam() {
        UUID gameId = UUID.randomUUID();
        UUID teamA = UUID.randomUUID();
        buffer.bufferLocation(gameId, location(teamA, 52.1));
        buffer.bufferLocation(gameId, location(teamA, 52.9));

        buffer.flush();

        Map<UUID, Object> perTeam = capturePublished().getPerTeamPayloads();
        assertEquals(1, perTeam.size());
        assertEquals(52.9, ((TeamLocationResponse) perTeam.get(teamA)).latitude());
    }

    @Test
    void flush_teamLocation_carriesTheTeamsOwnLocationAsThePayloadShape() {
        // The participant map reads message.payload as a single TeamLocation, so each slice
        // must be the bare response object rather than a list.
        UUID gameId = UUID.randomUUID();
        UUID teamA = UUID.randomUUID();
        buffer.bufferLocation(gameId, location(teamA, 52.1));

        buffer.flush();

        Object slice = capturePublished().getPerTeamPayloads().get(teamA);
        assertEquals(TeamLocationResponse.class, slice.getClass());
        assertEquals(teamA, ((TeamLocationResponse) slice).teamId());
    }

    @Test
    void flush_teamLocation_isIdempotent_secondFlushSendsNothing() {
        UUID gameId = UUID.randomUUID();
        buffer.bufferLocation(gameId, location(UUID.randomUUID(), 52.1));

        buffer.flush();
        buffer.flush();

        verify(webSocketPublisher, times(1)).publish(any());
    }

    @Test
    void flush_teamLocation_doesNotDependOnAdminSubscribers() {
        // A team must see its own position whether or not any admin has the map open.
        UUID gameId = UUID.randomUUID();
        buffer.bufferLocation(gameId, location(UUID.randomUUID(), 52.1));

        buffer.flush();

        verify(webSocketPublisher, times(1)).publish(any());
        verifyNoInteractions(adminTopicSubscriptionManager);
    }

    // --- admin map path ---

    @Test
    void flush_sendsOneSessionTargetedMessageForAllMapSubscribers() {
        UUID gameId = UUID.randomUUID();
        when(adminTopicSubscriptionManager.getActiveSubscribers(gameId, AdminTopics.MAP))
            .thenReturn(Set.of("admin-1", "admin-2"));
        buffer.buffer(gameId, List.of(location(UUID.randomUUID(), 52.1)));

        buffer.flush();

        WebSocketMessage sent = capturePublished();
        assertEquals(WebSocketEventTypes.LOCATION_UPDATE, sent.getType());
        assertTrue(sent.isAdminOnly());
        assertNull(sent.getTargetSessionId());
        // One publish for both admins, not one each.
        assertEquals(Set.of("admin-1", "admin-2"), Set.copyOf(sent.getTargetSessionIds()));
        assertEquals(1, ((List<?>) sent.getPayload()).size());
    }

    @Test
    void flush_adminMap_withNoSubscribers_publishesNothing() {
        UUID gameId = UUID.randomUUID();
        when(adminTopicSubscriptionManager.getActiveSubscribers(gameId, AdminTopics.MAP))
            .thenReturn(Set.of());
        buffer.buffer(gameId, List.of(location(UUID.randomUUID(), 52.1)));

        buffer.flush();

        verifyNoInteractions(webSocketPublisher);
    }

    @Test
    void flush_withNothingBuffered_doesNothing() {
        buffer.flush();
        verifyNoInteractions(webSocketPublisher);
        verifyNoInteractions(adminTopicSubscriptionManager);
    }

    private WebSocketMessage capturePublished() {
        ArgumentCaptor<WebSocketMessage> captor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(webSocketPublisher).publish(captor.capture());
        return captor.getValue();
    }

    private TeamLocationResponse location(UUID teamId, double latitude) {
        return new TeamLocationResponse(teamId, "De Watervlooien", "Snelle vlettenvaarders",
            latitude, 4.9, 3.2, 180.0, "2026-07-16T10:00:00Z");
    }
}
