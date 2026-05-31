package nl.vlettenrally.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import nl.vlettenrally.data.converter.PointEntryConverter;
import nl.vlettenrally.data.domain.PointEntry;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.dto.points.PointEntryResponse;
import nl.vlettenrally.data.enums.PointEntryType;
import nl.vlettenrally.data.repository.PointEntryRepository;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.websockets.WebSocketPublisher;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Single mutator of {@link Team#getTotalPoints()}.
 *
 * Every change is recorded as a PointEntry. The team's totalPoints is updated
 * by the same delta in the same transaction. After commit, an admin WebSocket
 * event is published so the Points Management page updates live.
 *
 * Both create and edit enforce that the resulting team totalPoints stays >= 0.
 */
@ApplicationScoped
public class PointsLedgerService {

    public static final String EVENT_POINT_ENTRY_CREATED = "POINT_ENTRY_CREATED";
    public static final String EVENT_POINT_ENTRY_UPDATED = "POINT_ENTRY_UPDATED";

    @Inject
    PointEntryRepository pointEntryRepository;

    @Inject
    TeamRepository teamRepository;

    @Inject
    WebSocketPublisher webSocketPublisher;

    @Inject
    PointEntryConverter pointEntryConverter;

    @Inject
    TransactionSynchronizationRegistry txRegistry;

    /**
     * Record a new ledger entry and adjust the team's totalPoints by {@code amount}.
     */
    @Transactional
    public PointEntry createEntry(UUID teamId, UUID gameId, PointEntryType type, int amount,
                                  Point location, String description,
                                  UUID sourceAchievementId, UUID createdBy) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Team not found: " + teamId));

        int newTotal = team.getTotalPoints() + amount;
        if (newTotal < 0) {
            throw new BadRequestException(
                "Resulting total points would be negative (" + newTotal + ")");
        }

        PointEntry entry = new PointEntry();
        entry.setTeamId(teamId);
        entry.setGameId(gameId);
        entry.setEntryType(type);
        entry.setOriginalAmount(amount);
        entry.setCurrentAmount(amount);
        entry.setSourceAchievementId(sourceAchievementId);
        entry.setLocation(location);
        entry.setDescription(description);
        entry.setCreatedBy(createdBy);
        PointEntry saved = pointEntryRepository.save(entry);

        team.setTotalPoints(newTotal);
        team.setUpdatedAt(Instant.now());
        teamRepository.save(team);

        broadcastAfterCommit(gameId, EVENT_POINT_ENTRY_CREATED, pointEntryConverter.toResponse(saved));
        return saved;
    }

    /**
     * Adjust the {@code currentAmount} of an existing entry. The team's
     * totalPoints changes by the delta (newAmount - currentAmount).
     */
    @Transactional
    public PointEntry editEntry(UUID entryId, int newAmount, String reason, UUID editedBy) {
        PointEntry entry = pointEntryRepository.findById(entryId)
                .orElseThrow(() -> new NotFoundException("Point entry not found: " + entryId));

        int delta = newAmount - entry.getCurrentAmount();
        if (delta == 0) {
            // No-op edit — still record the reason for auditability if provided
            entry.setEditedAt(Instant.now());
            entry.setEditedBy(editedBy);
            entry.setEditReason(reason);
            PointEntry saved = pointEntryRepository.save(entry);
            broadcastAfterCommit(entry.getGameId(), EVENT_POINT_ENTRY_UPDATED, pointEntryConverter.toResponse(saved));
            return saved;
        }

        Team team = teamRepository.findById(entry.getTeamId())
                .orElseThrow(() -> new NotFoundException("Team not found: " + entry.getTeamId()));

        int newTotal = team.getTotalPoints() + delta;
        if (newTotal < 0) {
            throw new BadRequestException(
                "Resulting total points would be negative (" + newTotal + ")");
        }

        entry.setCurrentAmount(newAmount);
        entry.setEditedAt(Instant.now());
        entry.setEditedBy(editedBy);
        entry.setEditReason(reason);
        PointEntry saved = pointEntryRepository.save(entry);

        team.setTotalPoints(newTotal);
        team.setUpdatedAt(Instant.now());
        teamRepository.save(team);

        broadcastAfterCommit(entry.getGameId(), EVENT_POINT_ENTRY_UPDATED, pointEntryConverter.toResponse(saved));
        return saved;
    }

    public Optional<PointEntry> findById(UUID id) {
        return pointEntryRepository.findById(id);
    }

    /**
     * Register a post-commit broadcast so admins never see a row that the
     * transaction later rolled back.
     */
    private void broadcastAfterCommit(UUID gameId, String type, PointEntryResponse payload) {
        try {
            txRegistry.registerInterposedSynchronization(new Synchronization() {
                @Override public void beforeCompletion() { }
                @Override public void afterCompletion(int status) {
                    if (status == Status.STATUS_COMMITTED) {
                        webSocketPublisher.sendToAdmins(gameId, type, payload);
                    }
                }
            });
        } catch (IllegalStateException noTx) {
            // No active transaction (shouldn't happen — methods are @Transactional),
            // fall back to immediate broadcast to avoid losing the event.
            Log.warn("No active transaction for ledger broadcast; sending immediately");
            webSocketPublisher.sendToAdmins(gameId, type, payload);
        }
    }
}
