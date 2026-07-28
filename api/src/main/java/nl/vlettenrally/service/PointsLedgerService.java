package nl.vlettenrally.service;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
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
import nl.vlettenrally.websockets.AdminTopics;
import nl.vlettenrally.websockets.WebSocketMessage;
import nl.vlettenrally.websockets.WebSocketPublisher;
import nl.vlettenrally.websockets.dto.WebSocketMessageType;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Every change is recorded as a PointEntry. The team's totalPoints is updated
 * by the same delta in the same transaction. After commit, an admin WebSocket
 * event is published so the Points Management page updates live.
 * <p>
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

    @Inject
    PointsTotalsCache pointsTotalsCache;

    @Inject
    nl.vlettenrally.websockets.StandingsBroadcastBuffer standingsBroadcastBuffer;

    /**
     * Record a new ledger entry. The team's running total is maintained in the
     * Valkey cache ({@link PointsTotalsCache}), not on the team row.
     */
    @Transactional
    public PointEntry createEntry(UUID teamId, UUID gameId, PointEntryType type, int amount,
                                  Point location, String description,
                                  UUID sourceAchievementId, UUID createdBy) {
        return createEntry(teamId, gameId, type, amount, location, description,
            sourceAchievementId, createdBy, null);
    }

    /**
     * As {@link #createEntry(UUID, UUID, PointEntryType, int, Point, String, UUID, UUID)},
     * but records the credited distance (meters) on the entry — used for
     * DISTANCE_BONUS entries so the ledger can show how far the points cover.
     */
    @Transactional
    public PointEntry createEntry(UUID teamId, UUID gameId, PointEntryType type, int amount,
                                  Point location, String description,
                                  UUID sourceAchievementId, UUID createdBy, Double distanceMeters) {
        teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Team not found: " + teamId));

        int oldTotal = pointsTotalsCache.get(teamId);
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
        entry.setDistanceMeters(distanceMeters);
        entry.setSourceAchievementId(sourceAchievementId);
        entry.setLocation(location);
        entry.setDescription(description);
        entry.setCreatedBy(createdBy);
        PointEntry saved = pointEntryRepository.save(entry);

        cacheTotalAfterCommit(teamId, newTotal);
        broadcastAfterCommit(gameId, EVENT_POINT_ENTRY_CREATED, pointEntryConverter.toResponse(saved));
        broadcastRankingAfterCommit(gameId, teamId, newTotal);
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
            Instant now = Instant.now();
            entry.setUpdatedAt(now);
            entry.setEditedAt(now);
            entry.setEditedBy(editedBy);
            entry.setEditReason(reason);
            PointEntry saved = pointEntryRepository.save(entry);
            broadcastAfterCommit(entry.getGameId(), EVENT_POINT_ENTRY_UPDATED, pointEntryConverter.toResponse(saved));
            // No total-change → no RANKING_UPDATE needed.
            return saved;
        }

        UUID teamId = entry.getTeamId();
        int oldTotal = pointsTotalsCache.get(teamId);
        int newTotal = oldTotal + delta;
        if (newTotal < 0) {
            throw new BadRequestException(
                "Resulting total points would be negative (" + newTotal + ")");
        }

        Instant now = Instant.now();
        entry.setCurrentAmount(newAmount);
        entry.setUpdatedAt(now);
        entry.setEditedAt(now);
        entry.setEditedBy(editedBy);
        entry.setEditReason(reason);
        PointEntry saved = pointEntryRepository.save(entry);

        cacheTotalAfterCommit(teamId, newTotal);
        broadcastAfterCommit(entry.getGameId(), EVENT_POINT_ENTRY_UPDATED, pointEntryConverter.toResponse(saved));
        broadcastRankingAfterCommit(entry.getGameId(), teamId, newTotal);
        automationEngine.onTeamPointsChanged(entry.getGameId(), teamId, oldTotal, newTotal);
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
        return accrueEntry(entryId, newAmount, null);
    }

    /**
     * As {@link #accrueEntry(UUID, int)}, but also updates the credited distance
     * (meters) on the entry — used for continuous DISTANCE_BONUS accrual.
     */
    @Transactional
    public PointEntry accrueEntry(UUID entryId, int newAmount, Double distanceMeters) {
        PointEntry entry = pointEntryRepository.findById(entryId)
                .orElseThrow(() -> new NotFoundException("Point entry not found: " + entryId));

        int delta = newAmount - entry.getCurrentAmount();
        if (delta == 0) {
            return entry;
        }

        UUID teamId = entry.getTeamId();
        int oldTotal = pointsTotalsCache.get(teamId);
        int newTotal = oldTotal + delta;
        if (newTotal < 0) {
            throw new BadRequestException(
                "Resulting total points would be negative (" + newTotal + ")");
        }

        entry.setCurrentAmount(newAmount);
        if (distanceMeters != null) {
            entry.setDistanceMeters(distanceMeters);
        }
        entry.setUpdatedAt(Instant.now());
        PointEntry saved = pointEntryRepository.save(entry);

        cacheTotalAfterCommit(teamId, newTotal);
        broadcastAfterCommit(entry.getGameId(), EVENT_POINT_ENTRY_UPDATED, pointEntryConverter.toResponse(saved));
        // Continuous distance accrual credits points every 100 m per team, so a standings
        // broadcast per accrual would fan out O(teams) work on every fix. Coalesce instead:
        // mark the game dirty and let StandingsBroadcastBuffer publish once per flush interval.
        bufferStandingsAfterCommit(entry.getGameId(), teamId);
        automationEngine.onTeamPointsChanged(entry.getGameId(), teamId, oldTotal, newTotal);
        return saved;
    }

    public Optional<PointEntry> findById(UUID id) {
        return pointEntryRepository.findById(id);
    }

    @Transactional
    public void resetTeamLedger(UUID teamId, UUID gameId) {
        teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Team not found: " + teamId));
        pointEntryRepository.deleteByTeamAndGame(teamId, gameId);
        // Clear distance accrual state so the next update re-baselines and
        // creates a fresh entry instead of accruing onto the deleted one.
        automationEngine.resetDistanceProgress(gameId, teamId);
        int newTotal = pointEntryRepository.sumCurrentAmountByTeam(teamId);
        cacheTotalAfterCommit(teamId, newTotal);
        broadcastRankingAfterCommit(gameId, teamId, newTotal);
        broadcastLedgerResetAfterCommit(gameId, teamId);
    }

    private void broadcastLedgerResetAfterCommit(UUID gameId, UUID teamId) {
        Map<String, Object> payload = Map.of("teamId", teamId, "gameId", gameId);
        try {
            txRegistry.registerInterposedSynchronization(new Synchronization() {
                @Override public void beforeCompletion() { }
                @Override public void afterCompletion(int status) {
                    if (status == Status.STATUS_COMMITTED) {
                        webSocketPublisher.sendToAdminTopic(gameId, AdminTopics.POINTS, "TEAM_LEDGER_RESET", payload);
                    }
                }
            });
        } catch (IllegalStateException noTx) {
            webSocketPublisher.sendToAdminTopic(gameId, AdminTopics.POINTS, "TEAM_LEDGER_RESET", payload);
        }
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
                        webSocketPublisher.sendToAdminTopic(gameId, AdminTopics.POINTS, type, payload);
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
     * Register a post-commit hook that marks the game's standings dirty in
     * {@link nl.vlettenrally.websockets.StandingsBroadcastBuffer}, which then publishes the
     * ranking / rival views once per flush interval instead of once per accrual. Used by the
     * continuous distance-accrual path; the discrete admin paths broadcast immediately.
     */
    private void bufferStandingsAfterCommit(UUID gameId, UUID teamId) {
        try {
            txRegistry.registerInterposedSynchronization(new Synchronization() {
                @Override public void beforeCompletion() { }
                @Override public void afterCompletion(int status) {
                    if (status == Status.STATUS_COMMITTED) {
                        standingsBroadcastBuffer.markDirty(gameId, teamId);
                    }
                }
            });
        } catch (IllegalStateException noTx) {
            standingsBroadcastBuffer.markDirty(gameId, teamId);
        }
    }

    /**
     * Write the new total to the cache after commit so reads during the same
     * broadcast cycle already see the fresh value without a ledger re-query.
     */
    private void cacheTotalAfterCommit(UUID teamId, int newTotal) {
        try {
            txRegistry.registerInterposedSynchronization(new Synchronization() {
                @Override public void beforeCompletion() { }
                @Override public void afterCompletion(int status) {
                    if (status == Status.STATUS_COMMITTED) {
                        pointsTotalsCache.set(teamId, newTotal);
                    }
                }
            });
        } catch (IllegalStateException noTx) {
            pointsTotalsCache.set(teamId, newTotal);
        }
    }

    /**
     * Publish standings after a point change, gated on game status. Done after
     * commit so subscribers never see standings from a rolled-back tx.
     * <p>
     * Only once the game is COMPLETED may the full ranking (every team's score)
     * be revealed — it is then broadcast to the whole game for the Rankings page.
     * While the game is still running, the full ranking would leak every team's
     * live score, so instead each affected team is sent only its own
     * [me + my rivals] view; the backend does the filtering, not the client.
     *
     * @param changedTeamId    the team whose total changed.
     * @param changedTeamTotal the new total for that team; passed explicitly so
     *                         the rival payload is built without a second cache
     *                         look-up or ordering dependency on cacheTotalAfterCommit.
     */
    private void broadcastRankingAfterCommit(UUID gameId, UUID changedTeamId, int changedTeamTotal) {
        try {
            txRegistry.registerInterposedSynchronization(new Synchronization() {
                @Override public void beforeCompletion() { }
                @Override public void afterCompletion(int status) {
                    if (status == Status.STATUS_COMMITTED) {
                        withRequestContext(() ->
                            publishStandings(gameId, Set.of(changedTeamId), Map.of(changedTeamId, changedTeamTotal)));
                    }
                }
            });
        } catch (IllegalStateException noTx) {
            Log.warn("No active transaction for ranking broadcast; sending immediately");
            withRequestContext(() ->
                publishStandings(gameId, Set.of(changedTeamId), Map.of(changedTeamId, changedTeamTotal)));
        }
    }

    /**
     * Run an after-commit body that reads the database. In afterCompletion the transaction is
     * already gone, so the EntityManager falls back to the CDI request context — which a bare
     * worker thread (e.g. the auto-backfill) doesn't have. Activate one for the duration when
     * missing; on request/scheduler threads the existing context is used as-is.
     */
    private void withRequestContext(Runnable body) {
        if (Arc.container() == null) {
            // No CDI container (plain unit tests invoking the callback directly).
            body.run();
            return;
        }
        ManagedContext requestContext = Arc.container().requestContext();
        if (requestContext.isActive()) {
            body.run();
            return;
        }
        requestContext.activate();
        try {
            body.run();
        } finally {
            requestContext.terminate();
        }
    }

    /**
     * Coalesced standings publish for the set of teams whose totals changed since the last
     * flush — called by {@link nl.vlettenrally.websockets.StandingsBroadcastBuffer} on its flush
     * interval for continuous distance accrual. Totals are read from {@link PointsTotalsCache}
     * (fresh, since each accrual wrote through post-commit), so no explicit totals are passed.
     * Runs under a CDI request context (not a JTA transaction) so the team/ranking reads get a
     * session on the scheduler thread without holding an enlisted JDBC connection across the blocking
     * Valkey fan-out — the same pattern the discrete path uses in {@code broadcastRankingAfterCommit}.
     */
    public void publishStandingsForTeams(UUID gameId, Set<UUID> changedTeamIds) {
        if (changedTeamIds == null || changedTeamIds.isEmpty()) {
            return;
        }
        withRequestContext(() -> publishStandings(gameId, changedTeamIds, Map.of()));
    }

    /**
     * Publish standings for one or more changed teams, gated on game status.
     * <p>
     * A team's total is taken from {@code totalOverrides} when present (the discrete path passes
     * the just-computed value so the payload needs no extra cache read), otherwise from
     * {@link PointsTotalsCache} (the coalesced path, where every changed team's total was written
     * through post-commit).
     */
    private void publishStandings(UUID gameId, Set<UUID> changedTeamIds, Map<UUID, Integer> totalOverrides) {
        // Staff on the admin ranking page always see the full live standings, regardless of
        // publish state or game status. Topic-gated: only sessions with a fresh ranking
        // heartbeat receive this, so it costs nothing when nobody's on the page.
        // Supplier, not a value: buildRanking costs one points lookup per checked-in team, and
        // as an eagerly-evaluated argument it was paid on every flush even with nobody on the page.
        webSocketPublisher.sendToAdminTopicLazily(
            gameId,
            AdminTopics.RANKING,
            WebSocketMessageType.RANKING_UPDATE.getValue(),
            () -> rankingService.buildRanking(gameId, totalOverrides));

        if (isRankingsPublished(gameId)) {
            // Standings are only player-visible once an admin has revealed them.
            // After that, a points correction re-broadcasts the updated ranking.
            webSocketPublisher.broadcastToGame(
                gameId,
                WebSocketMessageType.RANKING_UPDATE.getValue(),
                rankingService.buildRanking(gameId, totalOverrides));
        } else if (!isGameCompleted(gameId)) {
            // During the game, never send the full ranking. Refresh only the
            // rival views that include a changed team: each changed team itself
            // (its own total moved) and every team that picked one as a rival.
            List<Team> teams = teamRepository.findByGameId(gameId);
            Map<UUID, Team> byId = teams.stream()
                .collect(Collectors.toMap(Team::getId, t -> t));
            // Collected into one multiplexed envelope rather than published per team: during
            // continuous distance accrual nearly every team is affected each tick, which meant
            // a burst of one un-awaited publish per team against a small connection pool.
            Map<UUID, Object> perTeam = new HashMap<>();
            for (Team team : teams) {
                if (!team.isCheckedIn()) {
                    continue;
                }
                boolean affected = changedTeamIds.contains(team.getId())
                    || team.getRivals().stream().anyMatch(changedTeamIds::contains);
                if (!affected) {
                    continue;
                }
                perTeam.put(team.getId(), rivalPayload(team, byId, totalOverrides));
            }
            if (!perTeam.isEmpty()) {
                webSocketPublisher.publish(WebSocketMessage.multiplexed(
                    WebSocketMessageType.RIVAL_UPDATE.getValue(), gameId, perTeam));
            }
        }
    }

    private boolean isGameCompleted(UUID gameId) {
        return gameService.findById(gameId)
            .map(g -> g.getStatus() == Game.GameStatus.COMPLETED)
            .orElse(false);
    }

    private boolean isRankingsPublished(UUID gameId) {
        return gameService.findById(gameId)
            .map(g -> Boolean.TRUE.equals(g.getRankingsPublished()))
            .orElse(false);
    }

    /**
     * The team's rivals as {id, name, totalPoints} — the same shape
     * {@code TeamServiceImpl.setRivals} emits on a RIVAL_UPDATE.
     * A rival's total comes from {@code totalOverrides} when present (the discrete path supplies
     * the just-changed team's fresh total), otherwise from the cache (an L1 hit in the common
     * case; a Valkey read, or a ledger SUM on a cold miss, at worst).
     */
    private List<Map<String, Object>> rivalPayload(Team recipient, Map<UUID, Team> byId,
                                                   Map<UUID, Integer> totalOverrides) {
        return recipient.getRivals().stream()
            .map(byId::get)
            .filter(Objects::nonNull)
            .map(r -> {
                Integer override = totalOverrides.get(r.getId());
                int total = override != null ? override : pointsTotalsCache.get(r.getId());
                return Map.<String, Object>of("id", r.getId(), "name", r.getName(), "totalPoints", total);
            })
            .toList();
    }
}
