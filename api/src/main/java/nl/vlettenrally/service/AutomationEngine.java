package nl.vlettenrally.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.AutomationDistanceProgress;
import nl.vlettenrally.data.domain.AutomationExecution;
import nl.vlettenrally.data.domain.AutomationRule;
import nl.vlettenrally.data.domain.Checkpoint;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.LocationData;
import nl.vlettenrally.data.domain.PointEntry;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.domain.automation.AutomationTriggerConfig;
import nl.vlettenrally.data.enums.AutomationTriggerType;
import nl.vlettenrally.data.enums.PointEntryType;
import nl.vlettenrally.data.repository.AutomationDistanceProgressRepository;
import nl.vlettenrally.data.repository.AutomationExecutionRepository;
import nl.vlettenrally.data.repository.AutomationRuleRepository;
import nl.vlettenrally.data.repository.CheckpointRepository;
import nl.vlettenrally.data.repository.LocationDataRepository;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.websockets.WebSocketPublisher;
import nl.vlettenrally.websockets.dto.WebSocketMessageType;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Evaluates automation rules when domain events occur and executes their
 * actions. Hooked synchronously into the services that produce the events
 * (checkpoint passes, point changes, status transitions, location polling);
 * every public hook is exception-safe so an automation bug can never break
 * the calling flow.
 *
 * <h2>Re-entrancy</h2>
 * Action execution sets a thread-local guard. All discrete hooks no-op while
 * it is set, so automation-initiated point awards never trigger further
 * automations (depth limit of 1, preventing rule→points→rule loops).
 * Distance accrual is exempt: it is driven by external tracking data, not by
 * another rule firing, so a distance-driven overtake legitimately fires
 * RIVAL_OVERTAKE rules — whatever those rules do still runs under the guard.
 *
 * <h2>Dedup</h2>
 * Fire-once rules skip when an execution row exists for the same
 * (rule, team, contextKey); cooldown rules skip while the latest matching row
 * is younger than the cooldown. Repository reads come from the primary
 * datasource so replica lag cannot re-fire rules.
 */
@ApplicationScoped
public class AutomationEngine {

    /** Maximum lateness for a scheduled-time rule: prevents mass-firing when a
     *  rule is created long after its due time or a game's times are edited. */
    private static final Duration SCHEDULED_FIRE_WINDOW = Duration.ofHours(24);

    /** Distance points accrue per whole block of this many meters. */
    private static final double DISTANCE_BLOCK_METERS = 100.0;

    private static final ThreadLocal<Boolean> EXECUTING = ThreadLocal.withInitial(() -> false);

    @Inject
    AutomationRuleRepository ruleRepository;

    @Inject
    AutomationExecutionRepository executionRepository;

    @Inject
    AutomationDistanceProgressRepository distanceProgressRepository;

    @Inject
    TeamRepository teamRepository;

    @Inject
    CheckpointRepository checkpointRepository;

    @Inject
    GameService gameService;

    @Inject
    LocationDataRepository locationDataRepository;

    @Inject
    PointsLedgerService pointsLedgerService;

    @Inject
    AutomationActionExecutor actionExecutor;

    @Inject
    WebSocketPublisher webSocketPublisher;

    /**
     * A team's checkpoint pass was verified (auto or manual).
     *
     * @param passTime when the team was actually in the zone (the tracker's
     *                 pass time, not processing time) — rules with a
     *                 before/after time window (finish bonus/penalty) are
     *                 evaluated against this.
     */
    public void onCheckpointPass(UUID gameId, UUID teamId, UUID checkpointId, Instant passTime) {
        if (EXECUTING.get()) return;
        try {
            for (AutomationRule rule : enabledRules(gameId, AutomationTriggerType.CHECKPOINT_ENTRY)) {
                if (!rule.appliesToTeam(teamId)) continue;
                AutomationTriggerConfig config = rule.getTriggerConfig();
                UUID wanted = config != null ? config.checkpointId() : null;
                if (wanted != null && !wanted.equals(checkpointId)) continue;
                if (wanted == null && config != null && config.excludedCheckpointIds() != null
                        && config.excludedCheckpointIds().contains(checkpointId)) continue;
                if (!passesTimeWindow(rule, passTime)) continue;
                fireForTeam(rule, teamId, checkpointId.toString());
            }
        } catch (Exception e) {
            Log.errorf("Automation checkpoint hook failed for game %s team %s: %s", gameId, teamId, e.getMessage());
        }
    }

    /**
     * Evaluate the optional before/after window of a checkpoint rule: the
     * boundary time is configured like a scheduled time (absolute, or relative
     * to game start/end) and compared against the pass time. No comparison
     * configured = no restriction; an unresolvable boundary (e.g. relative to
     * an unset game time) never fires rather than firing unconditionally.
     */
    private boolean passesTimeWindow(AutomationRule rule, Instant passTime) {
        AutomationTriggerConfig config = rule.getTriggerConfig();
        if (config == null || config.comparison() == null) {
            return true;
        }
        if (passTime == null) {
            return false;
        }
        Game game = gameService.findById(rule.getGameId()).orElse(null);
        if (game == null) {
            return false;
        }
        Instant boundary = computeFireAt(config, game);
        if (boundary == null) {
            return false;
        }
        return (config.comparison() == AutomationTriggerConfig.PassComparison.BEFORE) == passTime.isBefore(boundary);
    }

    /** A game's status changed (scheduler transition or manual admin update). */
    public void onGameStatusChange(Game game, Game.GameStatus from, Game.GameStatus to) {
        if (EXECUTING.get()) return;
        try {
            for (AutomationRule rule : enabledRules(game.getId(), AutomationTriggerType.GAME_STATUS_CHANGE)) {
                AutomationTriggerConfig config = rule.getTriggerConfig();
                if (config == null || config.toStatus() != to) continue;
                fireGameLevel(rule, game.getId(), from + "->" + to);
            }
        } catch (Exception e) {
            Log.errorf("Automation status hook failed for game %s: %s", game.getId(), e.getMessage());
        }
    }

    /**
     * A team's totalPoints changed. Fires RIVAL_OVERTAKE rules when the changed
     * team crossed above a rival's total (only the changed team can overtake —
     * its rivals' totals did not move).
     */
    public void onTeamPointsChanged(UUID gameId, UUID teamId, int oldTotal, int newTotal) {
        if (EXECUTING.get()) return;
        if (newTotal <= oldTotal) return;
        try {
            List<AutomationRule> rules = enabledRules(gameId, AutomationTriggerType.RIVAL_OVERTAKE).stream()
                .filter(rule -> rule.appliesToTeam(teamId))
                .toList();
            if (rules.isEmpty()) return;

            Team team = teamRepository.findById(teamId).orElse(null);
            if (team == null || team.getRivals() == null) return;

            for (UUID rivalId : team.getRivals()) {
                Optional<Team> rival = teamRepository.findById(rivalId);
                if (rival.isEmpty()) continue;
                int rivalTotal = rival.get().getTotalPoints();
                if (oldTotal <= rivalTotal && newTotal > rivalTotal) {
                    for (AutomationRule rule : rules) {
                        fireForTeam(rule, teamId, rivalId.toString());
                    }
                }
            }
        } catch (Exception e) {
            Log.errorf("Automation overtake hook failed for game %s team %s: %s", gameId, teamId, e.getMessage());
        }
    }

    /**
     * The location poller reported a team's cumulative traveled distance
     * (meters, on-course + off-course). Credits distance points through the
     * game's DISTANCE_TRAVELED rule(s).
     * <p>
     * Points only accrue while the game is ACTIVE — the game status is the single
     * source of truth, so there is no separate enable/disable toggle to keep in
     * sync. The caller (the location ingest choke point) determines {@code active}
     * once per fix. Meters reported outside an active game (e.g. a tracker still
     * pushing before the start signal or after the finish) advance the baseline
     * without credit, so they are never credited retroactively once the game starts.
     */
    public void onDistanceUpdate(UUID gameId, UUID teamId, double reportedMeters, boolean active) {
        try {
            for (AutomationRule rule : ruleRepository.findByGameIdAndTriggerType(
                    gameId, AutomationTriggerType.DISTANCE_TRAVELED)) {
                if (!rule.appliesToTeam(teamId)) continue;
                accrueDistance(rule, teamId, reportedMeters, active);
            }
        } catch (Exception e) {
            Log.errorf("Automation distance hook failed for game %s team %s: %s", gameId, teamId, e.getMessage());
        }
    }

    private void accrueDistance(AutomationRule rule, UUID teamId, double reportedMeters, boolean active) {
        AutomationDistanceProgress progress = distanceProgressRepository
            .findByRuleAndTeam(rule.getId(), teamId).orElse(null);

        if (progress == null) {
            // First sight: baseline only, no credit for distance traveled before tracking.
            progress = new AutomationDistanceProgress();
            progress.setRuleId(rule.getId());
            progress.setTeamId(teamId);
            progress.setLastReportedMeters(reportedMeters);
            distanceProgressRepository.save(progress);
            return;
        }

        double delta = reportedMeters - progress.getLastReportedMeters();
        progress.setLastReportedMeters(reportedMeters);

        Integer pointsPer100m = rule.getTriggerConfig() != null
            ? rule.getTriggerConfig().pointsPer100m() : null;
        if (delta > 0 && active && pointsPer100m != null && pointsPer100m > 0) {
            double leftover = progress.getLeftoverMeters() + delta;
            int blocks = (int) Math.floor(leftover / DISTANCE_BLOCK_METERS);
            if (blocks > 0) {
                leftover -= blocks * DISTANCE_BLOCK_METERS;
                creditDistancePoints(rule, teamId, progress, blocks * pointsPer100m);
            }
            progress.setLeftoverMeters(leftover);
        }
        // delta < 0: provider counter reset — rebaseline without credit.

        progress.setUpdatedAt(Instant.now());
        distanceProgressRepository.save(progress);
    }

    private void creditDistancePoints(AutomationRule rule, UUID teamId,
                                      AutomationDistanceProgress progress, int newPoints) {
        int total = progress.getCreditedPoints() + newPoints;
        if (progress.getPointEntryId() == null) {
            PointEntry entry = pointsLedgerService.createEntry(teamId, rule.getGameId(),
                PointEntryType.DISTANCE_BONUS, total, null, "Afstandspunten", null, null);
            progress.setPointEntryId(entry.getId());
        } else {
            pointsLedgerService.accrueEntry(progress.getPointEntryId(), total);
        }
        progress.setCreditedPoints(total);
    }

    /** Scheduler tick: evaluate SCHEDULED_TIME and TEAM_INACTIVITY rules. */
    public void evaluateTimeAndInactivity(Instant now) {
        if (EXECUTING.get()) return;
        evaluateScheduledTime(now);
        evaluateInactivity(now);
    }

    private void evaluateScheduledTime(Instant now) {
        for (AutomationRule rule : ruleRepository.findEnabledByTriggerType(AutomationTriggerType.SCHEDULED_TIME)) {
            try {
                Game game = gameService.findById(rule.getGameId()).orElse(null);
                if (game == null) continue;

                Instant fireAt = computeFireAt(rule.getTriggerConfig(), game);
                if (fireAt == null || now.isBefore(fireAt)) continue;
                if (fireAt.isBefore(now.minus(SCHEDULED_FIRE_WINDOW))) continue;

                fireGameLevel(rule, game.getId(), null);
            } catch (Exception e) {
                Log.errorf("Automation scheduled rule %s failed: %s", rule.getId(), e.getMessage());
            }
        }
    }

    private static Instant computeFireAt(AutomationTriggerConfig config, Game game) {
        if (config == null || config.mode() == null) return null;
        if (config.mode() == AutomationTriggerConfig.ScheduleMode.ABSOLUTE) {
            return config.absoluteTime();
        }
        if (config.relativeTo() == null || config.offsetMinutes() == null) return null;
        Instant anchor = config.relativeTo() == AutomationTriggerConfig.RelativeAnchor.GAME_START
            ? game.getStartSignalTime()
            : game.getEndTime();
        return anchor != null ? anchor.plus(Duration.ofMinutes(config.offsetMinutes())) : null;
    }

    private void evaluateInactivity(Instant now) {
        for (AutomationRule rule : ruleRepository.findEnabledByTriggerType(AutomationTriggerType.TEAM_INACTIVITY)) {
            try {
                Game game = gameService.findById(rule.getGameId()).orElse(null);
                if (game == null || game.getStatus() != Game.GameStatus.ACTIVE) continue;

                Integer minutes = rule.getTriggerConfig() != null
                    ? rule.getTriggerConfig().inactiveMinutes() : null;
                if (minutes == null || minutes <= 0) continue;
                Instant threshold = now.minus(Duration.ofMinutes(minutes));

                for (Team team : teamRepository.findByGameId(game.getId())) {
                    if (!team.isCheckedIn() || !rule.appliesToTeam(team.getId())) continue;
                    // Teams that never reported are inactive once the game has
                    // been running longer than the threshold.
                    Instant lastSeen = locationDataRepository.findLatestByTeamId(team.getId())
                        .map(LocationData::getLocationTime)
                        .orElse(game.getStartSignalTime());
                    if (lastSeen == null || lastSeen.isAfter(threshold)) continue;
                    fireForTeam(rule, team.getId(), null);
                }
            } catch (Exception e) {
                Log.errorf("Automation inactivity rule %s failed: %s", rule.getId(), e.getMessage());
            }
        }
    }

    private List<AutomationRule> enabledRules(UUID gameId, AutomationTriggerType type) {
        return ruleRepository.findByGameIdAndTriggerType(gameId, type).stream()
            .filter(AutomationRule::isEnabled)
            .toList();
    }

    /** Fire a rule for one team, subject to dedup. */
    private void fireForTeam(AutomationRule rule, UUID teamId, String contextKey) {
        if (isDuplicate(rule, teamId, contextKey)) return;
        Team team = teamRepository.findById(teamId).orElse(null);
        if (team == null) return;
        execute(rule, List.of(team), teamId, contextKey);
    }

    /**
     * Fire a rule at game level (one execution, teamId null), subject to dedup.
     * Actions run against the rule's target teams, or all checked-in teams.
     */
    private void fireGameLevel(AutomationRule rule, UUID gameId, String contextKey) {
        if (isDuplicate(rule, null, contextKey)) return;
        List<Team> targets = teamRepository.findByGameId(gameId).stream()
            .filter(Team::isCheckedIn)
            .filter(team -> rule.appliesToTeam(team.getId()))
            .toList();
        if (targets.isEmpty()) return;
        execute(rule, targets, null, contextKey);
    }

    private boolean isDuplicate(AutomationRule rule, UUID teamId, String contextKey) {
        if (rule.isFireOnce()) {
            return executionRepository.existsByRuleTeamAndContext(rule.getId(), teamId, contextKey);
        }
        if (rule.getCooldownMinutes() != null && rule.getCooldownMinutes() > 0) {
            return executionRepository.findLatestFiredAt(rule.getId(), teamId, contextKey)
                .map(last -> last.isAfter(Instant.now().minus(Duration.ofMinutes(rule.getCooldownMinutes()))))
                .orElse(false);
        }
        return false;
    }

    private void execute(AutomationRule rule, List<Team> targets, UUID teamId, String contextKey) {
        AutomationActionExecutor.Result result;
        EXECUTING.set(true);
        try {
            result = actionExecutor.execute(rule, targets, buildActionContext(rule, contextKey));
        } finally {
            EXECUTING.remove();
        }

        AutomationExecution execution = new AutomationExecution();
        execution.setRuleId(rule.getId());
        execution.setGameId(rule.getGameId());
        execution.setTeamId(teamId);
        execution.setContextKey(contextKey);
        execution.setStatus(result.status());
        execution.setDetail(result.detail());
        executionRepository.save(execution);

        Log.infof("Automation '%s' (%s) fired for %s: %s",
            rule.getName(), rule.getId(), teamId != null ? "team " + teamId : "game", result.status());

        Map<String, Object> payload = new HashMap<>();
        payload.put("executionId", execution.getId());
        payload.put("ruleId", rule.getId());
        payload.put("ruleName", rule.getName());
        payload.put("teamId", teamId);
        payload.put("contextKey", contextKey);
        payload.put("status", execution.getStatus());
        payload.put("firedAt", execution.getFiredAt().toString());
        webSocketPublisher.sendToAdmins(rule.getGameId(),
            WebSocketMessageType.AUTOMATION_TRIGGERED.getValue(), payload);
    }

    /**
     * Resolve the firing-context names a rule's message/notification
     * placeholders may use, from the {@code contextKey}: the checkpoint name for
     * CHECKPOINT_ENTRY, the overtaken rival's name for RIVAL_OVERTAKE.
     */
    private AutomationActionExecutor.ActionContext buildActionContext(AutomationRule rule, String contextKey) {
        UUID id = parseUuid(contextKey);
        if (id == null) {
            return AutomationActionExecutor.ActionContext.EMPTY;
        }
        return switch (rule.getTriggerType()) {
            case CHECKPOINT_ENTRY -> new AutomationActionExecutor.ActionContext(
                checkpointRepository.findById(id).map(Checkpoint::getName).orElse(null), null);
            case RIVAL_OVERTAKE -> new AutomationActionExecutor.ActionContext(
                null, teamRepository.findById(id).map(Team::getName).orElse(null));
            default -> AutomationActionExecutor.ActionContext.EMPTY;
        };
    }

    private static UUID parseUuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
