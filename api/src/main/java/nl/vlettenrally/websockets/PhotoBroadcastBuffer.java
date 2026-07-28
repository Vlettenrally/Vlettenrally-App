package nl.vlettenrally.websockets;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.dto.picture.PublicPictureResponse;
import nl.vlettenrally.websockets.dto.WebSocketMessageType;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Buffers newly-approved pictures per game and flushes them as a single batched
 * {@code PICTURE_APPROVED} WebSocket message to every team in the game, on a configurable
 * interval (see {@code vlettenrally.pictures.broadcast-interval}). Mirrors
 * {@link LocationBroadcastBuffer}: a moderator approving a run of photos produces one
 * fan-out per interval rather than a message per photo, and the frontend reveals the batch
 * one-by-one so it reads as a live stream.
 * <p>
 * Pictures are keyed by id between flushes, so a double-approve collapses to one entry.
 * Only the public projection is broadcast — a shared wall must never carry another team's
 * moderation metadata.
 */
@ApplicationScoped
public class PhotoBroadcastBuffer {

    /** gameId → (pictureId → approved picture) */
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, PublicPictureResponse>> pending =
            new ConcurrentHashMap<>();

    @Inject
    WebSocketPublisher webSocketPublisher;

    /**
     * Queue an approved picture for the next flush. Thread-safe; a repeat id overwrites.
     */
    public void buffer(UUID gameId, PublicPictureResponse picture) {
        if (gameId == null || picture == null) {
            return;
        }
        pending.computeIfAbsent(gameId, k -> new ConcurrentHashMap<>()).put(picture.id(), picture);
    }

    /**
     * Flush pending approvals to each game's teams. Runs on the configured interval; games
     * with nothing pending are skipped without touching Valkey.
     */
    @Scheduled(every = "{vlettenrally.pictures.broadcast-interval}",
               concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void flush() {
        if (pending.isEmpty()) {
            return;
        }

        for (Map.Entry<UUID, ConcurrentHashMap<UUID, PublicPictureResponse>> entry : pending.entrySet()) {
            UUID gameId = entry.getKey();
            ConcurrentHashMap<UUID, PublicPictureResponse> gameBuffer = entry.getValue();

            // Swap out the game's buffer so approvals arriving during flush land in a fresh map.
            if (!pending.remove(gameId, gameBuffer)) {
                continue;
            }
            if (gameBuffer.isEmpty()) {
                continue;
            }

            // Newest first, matching the REST ordering the wall renders with.
            List<PublicPictureResponse> batch = gameBuffer.values().stream()
                    .sorted(Comparator.comparing(PublicPictureResponse::uploadedAt,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();

            Log.debugf("Photo broadcast flush: sending %d approved picture(s) for game %s",
                    batch.size(), gameId);

            WebSocketMessage message = new WebSocketMessage(
                    WebSocketMessageType.PICTURE_APPROVED.getValue(), batch)
                    .withTargetGame(gameId)
                    .forTeamOnly();
            webSocketPublisher.publish(message);
        }
    }
}
