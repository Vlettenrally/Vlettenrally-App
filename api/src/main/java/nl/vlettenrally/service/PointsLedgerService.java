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
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.PointEntry;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.dto.points.PointEntryResponse;
import nl.vlettenrally.data.enums.PointEntryType;
import nl.vlettenrally.data.repository.PointEntryRepository;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.websockets.WebSocketPublisher;
import nl.vlettenrally.websockets.dto.WebSocketMessageType;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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
    GameService gameService;

    @Inject
    RankingService rankingService;

    @Inject
    TransactionSynchronizationRegistry txRegistry;

    @Inject
    AutomationEngine automationEngine;

    /**
     * Record a new ledger entry and adjust the team's totalPoints by {@code amount}.
     */
    @Transactional
    public PointEntry createEntry(UUID teamId, UUID gameId, PointEntryType type, int amount,
                                  Point location, String description,
                                  UUID sourceAchievementId, UUID createdBy) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Team not found: " + teamId));

        int oldTotal = team.getTotalPoints();
        int newTotal = oldTotal + amount;
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
        broadcastRankingAfterCommit(gameId, teamId);
        automationEngine.onTeamPointsChanged(gameId, teamId, oldTotal, newTotal);
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
            // No total-change → no RANKING_UPDATE needed.
            return saved;
        }

        Team team = teamRepository.findById(entry.getTeamId())
                .orElseThrow(() -> new NotFoundException("Team not found: " + entry.getTeamId()));

        int oldTotal = team.getTotalPoints();
        int newTotal = oldTotal + delta;
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
        broadcastRankingAfterCommit(entry.getGameId(), entry.getTeamId());
        automationEngine.onTeamPointsChanged(entry.getGameId(), entry.getTeamId(), oldTotal, newTotal);
        return saved;
    }

    /**
     * Grow (or shrink) an automation-managed entry to {@code newAmount} without
     * touching the editedAt/editedBy/editReason audit fields — those mark admin
     * edits, while this is continuous system accrual (distance points). The
     * team's totalPoints changes by the delta, with the same negative-total
     * check and post-commit broadcasts as {@link #editEntry}.
     */
    @Transactional
    public PointEntry accrueEntry(UUID entryId, int newAmount) {
        PointEntry entry = pointEntryRepository.findById(entryId)
                .orElseThrow(() -> new NotFoundException("Point entry not found: " + entryId));

        int delta = newAmount - entry.getCurrentAmount();
        if (delta == 0) {
            return entry;
        }

        Team team = teamRepository.findById(entry.getTeamId())
                .orElseThrow(() -> new NotFoundException("Team not found: " + entry.getTeamId()));

        int oldTotal = team.getTotalPoints();
        int newTotal = oldTotal + delta;
        if (newTotal < 0) {
            throw new BadRequestException(
                "Resulting total points would be negative (" + newTotal + ")");
        }

        entry.setCurrentAmount(newAmount);
        PointEntry saved = pointEntryRepository.save(entry);

        team.setTotalPoints(newTotal);
        team.setUpdatedAt(Instant.now());
        teamRepository.save(team);

        broadcastAfterCommit(entry.getGameId(), EVENT_POINT_ENTRY_UPDATED, pointEntryConverter.toResponse(saved));
        broadcastRankingAfterCommit(entry.getGameId(), entry.getTeamId());
        automationEngine.onTeamPointsChanged(entry.getGameId(), entry.getTeamId(), oldTotal, newTotal);
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

    /**
     * Publish standings after a point change, gated on game status. Done after
     * commit so subscribers never see standings from a rolled-back tx.
     *
     * Only once the game is COMPLETED may the full ranking (every team's score)
     * be revealed — it is then broadcast to the whole game for the Rankings page.
     * While the game is still running, the full ranking would leak every team's
     * live score, so instead each affected team is sent only its own
     * [me + my rivals] view; the backend does the filtering, not the client.
     *
     * @param changedTeamId the team whose total changed; used to decide which
     *                      teams' rival views need refreshing during the game.
     */
    private void broadcastRankingAfterCommit(UUID gameId, UUID changedTeamId) {
        try {
            txRegistry.registerInterposedSynchronization(new Synchronization() {
                @Override public void beforeCompletion() { }
                @Override public void afterCompletion(int status) {
                    if (status == Status.STATUS_COMMITTED) {
                        publishStandings(gameId, changedTeamId);
                    }
                }
            });
        } catch (IllegalStateException noTx) {
            Log.warn("No active transaction for ranking broadcast; sending immediately");
            publishStandings(gameId, changedTeamId);
        }
    }

    private void publishStandings(UUID gameId, UUID changedTeamId) {
        if (isGameCompleted(gameId)) {
            // Scores are public once the game is over — broadcast the full ranking.
            webSocketPublisher.broadcastToGame(
                gameId,
                WebSocketMessageType.RANKING_UPDATE.getValue(),
                rankingService.buildRanking(gameId));
        } else {
            // During the game, never send the full ranking. Refresh only the
            // rival views that include the changed team: the changed team itself
            // (its own total moved) and every team that picked it as a rival.
            List<Team> teams = teamRepository.findByGameId(gameId);
            Map<UUID, Team> byId = teams.stream()
                .collect(Collectors.toMap(Team::getId, t -> t));
            for (Team team : teams) {
                if (!team.isCheckedIn()) {
                    continue;
                }
                boolean affected = team.getId().equals(changedTeamId)
                    || team.getRivals().contains(changedTeamId);
                if (!affected) {
                    continue;
                }
                webSocketPublisher.sendToTeam(
                    gameId,
                    team.getId(),
                    WebSocketMessageType.RIVAL_UPDATE.getValue(),
                    rivalPayload(team, byId));
            }
        }
    }

    private boolean isGameCompleted(UUID gameId) {
        return gameService.findById(gameId)
            .map(g -> g.getStatus() == Game.GameStatus.COMPLETED)
            .orElse(false);
    }

    /**
     * The team's rivals as {id, name, totalPoints} — the same shape
     * {@code TeamServiceImpl.setRivals} emits on a RIVAL_UPDATE.
     */
    private List<Map<String, Object>> rivalPayload(Team team, Map<UUID, Team> byId) {
        return team.getRivals().stream()
            .map(byId::get)
            .filter(Objects::nonNull)
            .map(r -> Map.<String, Object>of(
                "id", r.getId(),
                "name", r.getName(),
                "totalPoints", r.getTotalPoints()))
            .toList();
    }
}
