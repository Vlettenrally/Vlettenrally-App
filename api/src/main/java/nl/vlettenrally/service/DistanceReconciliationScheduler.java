package nl.vlettenrally.service;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.repository.TeamRepository;

import java.time.Instant;

/**
 * Periodically re-derives every active game's teams' travelled distance from scratch
 * ({@link TeamDistanceCalculator#recomputeFromScratch}) and re-feeds it through
 * {@link AutomationEngine#onDistanceUpdate}, as an independent safety net against drift in the
 * incrementally-tracked {@code team_distance_progress} accumulator — from any cause, not just the
 * one concurrency race this mechanism was originally added to guard against (see
 * {@link nl.vlettenrally.data.repository.jpa.JpaTeamDistanceProgressRepository}).
 * <p>
 * A full recompute is the only way to detect drift that originated before a given call started
 * reading state, since it compares against an independent source of truth (the immutable
 * {@code location_data} fixes) rather than trusting the mutable accumulator. In the healthy case
 * this is a no-op: {@code accrueDistance}'s delta-based crediting only acts when the recomputed
 * value actually differs from what was already recorded.
 * <p>
 * Runs on a single instance at a time — leader election via {@link PollingLeader} (shared with the
 * other background schedulers).
 */
@ApplicationScoped
public class DistanceReconciliationScheduler {

    @Inject
    GameService gameService;

    @Inject
    TeamRepository teamRepository;

    @Inject
    TeamDistanceCalculator teamDistanceCalculator;

    @Inject
    AutomationEngine automationEngine;

    @Inject
    LocationIngestService locationIngestService;

    @Inject
    PollingLeader pollingLeader;

    @Scheduled(every = "{vlettenrally.distance.reconciliation-interval}")
    void reconcile() {
        if (!pollingLeader.tryAcquireOrRenew()) {
            return;
        }
        for (Game game : gameService.findByStatus(Game.GameStatus.ACTIVE)) {
            for (Team team : teamRepository.findByGameId(game.getId())) {
                if (team.getTrackerId() == null && !team.isTraccarEnabled()) {
                    // Never had a location source, so it can have no location_data to reconcile.
                    continue;
                }
                try {
                    reconcileTeam(game, team);
                } catch (Exception e) {
                    Log.errorf("Distance reconciliation failed for game %s team %s: %s",
                        game.getId(), team.getId(), e.getMessage());
                }
            }
        }
    }

    @Transactional
    void reconcileTeam(Game game, Team team) {
        double cumulative = teamDistanceCalculator.recomputeFromScratch(team.getId());
        LocationIngestService.IngestGate gate =
            locationIngestService.computeGate(game.getId(), team.getId(), Instant.now());
        automationEngine.onDistanceUpdate(game.getId(), team.getId(), cumulative, gate.scoringActive());
    }
}
