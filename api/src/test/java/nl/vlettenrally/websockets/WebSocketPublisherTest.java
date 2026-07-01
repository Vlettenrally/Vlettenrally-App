package nl.vlettenrally.websockets;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for WebSocketPublisher.
 * Verifies that each publish method constructs the correct WebSocketMessage
 * and delegates to ValkeyPubSubService.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketPublisherTest {

    @Mock
    ValkeyPubSubService valkeyPubSubService;

    @Mock
    AdminTopicSubscriptionManager adminTopicSubscriptionManager;

    @InjectMocks
    WebSocketPublisher publisher;

    private final UUID gameId = UUID.randomUUID();
    private final UUID teamId = UUID.randomUUID();

    @Test
    void broadcast_sendsMessageWithNoTargeting() {
        publisher.broadcast("GAME_STATUS", "started");

        ArgumentCaptor<WebSocketMessage> captor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(valkeyPubSubService).publish(captor.capture());

        WebSocketMessage msg = captor.getValue();
        assertEquals("GAME_STATUS", msg.getType());
        assertEquals("started", msg.getPayload());
        assertNull(msg.getTargetGameId());
        assertNull(msg.getTargetTeamId());
        assertFalse(msg.isAdminOnly());
        assertNotNull(msg.getTimestamp());
    }

    @Test
    void broadcastToGame_sendsMessageWithGameTarget() {
        publisher.broadcastToGame(gameId, "RANKING_UPDATE", "{\"rank\":1}");

        ArgumentCaptor<WebSocketMessage> captor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(valkeyPubSubService).publish(captor.capture());

        WebSocketMessage msg = captor.getValue();
        assertEquals("RANKING_UPDATE", msg.getType());
        assertEquals("{\"rank\":1}", msg.getPayload());
        assertEquals(gameId, msg.getTargetGameId());
        assertNull(msg.getTargetTeamId());
        assertFalse(msg.isAdminOnly());
    }

    @Test
    void sendToTeam_sendsMessageWithGameAndTeamTarget() {
        publisher.sendToTeam(gameId, teamId, "message:new", "hello");

        ArgumentCaptor<WebSocketMessage> captor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(valkeyPubSubService).publish(captor.capture());

        WebSocketMessage msg = captor.getValue();
        assertEquals("message:new", msg.getType());
        assertEquals("hello", msg.getPayload());
        assertEquals(gameId, msg.getTargetGameId());
        assertEquals(teamId, msg.getTargetTeamId());
        assertFalse(msg.isAdminOnly());
    }

    @Test
    void sendToAdmins_sendsAdminOnlyMessage() {
        publisher.sendToAdmins(gameId, "ADMIN_ALERT", "alert!");

        ArgumentCaptor<WebSocketMessage> captor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(valkeyPubSubService).publish(captor.capture());

        WebSocketMessage msg = captor.getValue();
        assertEquals("ADMIN_ALERT", msg.getType());
        assertEquals("alert!", msg.getPayload());
        assertEquals(gameId, msg.getTargetGameId());
        assertNull(msg.getTargetTeamId());
        assertTrue(msg.isAdminOnly());
    }

    @Test
    void sendToAdminTopic_sendsSessionTargetedAdminOnlyMessages() {
        when(adminTopicSubscriptionManager.getActiveSubscribers(gameId, "pictures"))
            .thenReturn(Set.of("session-a", "session-b"));

        publisher.sendToAdminTopic(gameId, "pictures", "PICTURE_PENDING", "payload");

        ArgumentCaptor<WebSocketMessage> captor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(valkeyPubSubService, times(2)).publish(captor.capture());

        List<WebSocketMessage> sent = captor.getAllValues();
        for (WebSocketMessage msg : sent) {
            assertEquals("PICTURE_PENDING", msg.getType());
            assertEquals("payload", msg.getPayload());
            assertEquals(gameId, msg.getTargetGameId());
            assertTrue(msg.isAdminOnly());
            assertNotNull(msg.getTargetSessionId());
        }
        Set<String> targeted = Set.of(sent.get(0).getTargetSessionId(), sent.get(1).getTargetSessionId());
        assertEquals(Set.of("session-a", "session-b"), targeted);
    }

    @Test
    void sendToAdminTopic_withNoActiveSubscribers_doesNotPublish() {
        when(adminTopicSubscriptionManager.getActiveSubscribers(gameId, "pictures"))
            .thenReturn(Set.of());

        publisher.sendToAdminTopic(gameId, "pictures", "PICTURE_PENDING", "payload");

        verify(valkeyPubSubService, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void publish_delegatesCustomMessageDirectly() {
        WebSocketMessage custom = new WebSocketMessage("CUSTOM", "data")
            .withTargetGame(gameId)
            .withTargetTeam(teamId)
            .forAdminOnly();

        publisher.publish(custom);

        verify(valkeyPubSubService).publish(custom);
    }

    @Test
    void broadcastToGame_withNullPayload_stillPublishes() {
        publisher.broadcastToGame(gameId, "GAME_STATUS", null);

        ArgumentCaptor<WebSocketMessage> captor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(valkeyPubSubService).publish(captor.capture());

        WebSocketMessage msg = captor.getValue();
        assertEquals("GAME_STATUS", msg.getType());
        assertNull(msg.getPayload());
        assertEquals(gameId, msg.getTargetGameId());
    }
}
