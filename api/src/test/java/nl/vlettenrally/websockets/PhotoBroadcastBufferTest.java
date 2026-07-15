package nl.vlettenrally.websockets;

import nl.vlettenrally.data.dto.picture.PublicPictureResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link PhotoBroadcastBuffer}: buffered approvals flush as a single batched,
 * game-targeted, team-only message; ids dedupe; an empty buffer touches nothing.
 */
@ExtendWith(MockitoExtension.class)
class PhotoBroadcastBufferTest {

    @Mock WebSocketPublisher webSocketPublisher;
    @InjectMocks PhotoBroadcastBuffer buffer;

    @Test
    void flush_sendsOneBatchedTeamOnlyMessagePerGame() {
        UUID gameId = UUID.randomUUID();
        buffer.buffer(gameId, picture("2026-07-16T10:00:00Z"));
        buffer.buffer(gameId, picture("2026-07-16T10:01:00Z"));

        buffer.flush();

        ArgumentCaptor<WebSocketMessage> captor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(webSocketPublisher).publish(captor.capture());
        WebSocketMessage sent = captor.getValue();
        assertEquals("PICTURE_APPROVED", sent.getType());
        assertEquals(gameId, sent.getTargetGameId());
        assertTrue(sent.isTeamOnly());
        assertEquals(2, ((List<?>) sent.getPayload()).size());
    }

    @Test
    void buffer_dedupesByPictureId() {
        UUID gameId = UUID.randomUUID();
        UUID pictureId = UUID.randomUUID();
        buffer.buffer(gameId, pictureWithId(pictureId, "2026-07-16T10:00:00Z"));
        buffer.buffer(gameId, pictureWithId(pictureId, "2026-07-16T10:05:00Z"));

        buffer.flush();

        ArgumentCaptor<WebSocketMessage> captor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(webSocketPublisher).publish(captor.capture());
        assertEquals(1, ((List<?>) captor.getValue().getPayload()).size());
    }

    @Test
    void flush_withNothingBuffered_doesNothing() {
        buffer.flush();
        verifyNoInteractions(webSocketPublisher);
    }

    @Test
    void flush_isIdempotent_secondFlushSendsNothing() {
        UUID gameId = UUID.randomUUID();
        buffer.buffer(gameId, picture("2026-07-16T10:00:00Z"));

        buffer.flush();
        buffer.flush();

        // The first flush drains the buffer; the second has nothing to send.
        verify(webSocketPublisher, times(1)).publish(any());
    }

    private PublicPictureResponse picture(String uploadedAt) {
        return pictureWithId(UUID.randomUUID(), uploadedAt);
    }

    private PublicPictureResponse pictureWithId(UUID id, String uploadedAt) {
        return new PublicPictureResponse(id, UUID.randomUUID(), "image/jpeg",
            "https://storj.example/view/" + id, uploadedAt);
    }
}
