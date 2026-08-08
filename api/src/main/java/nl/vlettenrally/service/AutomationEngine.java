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
import nl.vlettenrally.data.enums.PictureStatus;
import nl.vlettenrally.data.enums.PointEntryType;
import nl.vlettenrally.data.repository.AutomationDistanceProgressRepository;
import nl.vlettenrally.data.repository.AutomationExecutionRepository;
import nl.vlettenrally.data.repository.AutomationRuleRepository;
import nl.vlettenrally.data.repository.CheckpointRepository;
import nl.vlettenrally.data.repository.LocationDataRepository;
import nl.vlettenrally.data.repository.PictureRepository;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.observability.AutomationMetrics;
import nl.vlettenrally.observability.AutomationMetrics.Hook;
import nl.vlettenrally.observability.AutomationMetrics.SkipReason;
import nl.vlettenrally.websockets.AdminTopics;
import nl.vlettenrally.websockets.WebSocketPublisher;
import nl.vlettenrally.websockets.dto.WebSocketMessageType;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    PictureRepository pictureRepository;

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

    @Inject
    PointsTotalsCache pointsTotalsCache;

    @Inject
    AutomationMetrics metrics;

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
                List<UUID> wanted = config != null ? config.effectiveCheckpointIds() : List.of();
                if (!wanted.isEmpty()) {
                    if (!wanted.contains(checkpointId)) continue;
                } else if (config != null && config.excludedCheckpointIds() != null
                        && config.excludedCheckpointIds().contains(checkpointId)) {
                    continue;
                }
                if (!passesTimeWindow(rule, passTime)) continue;
                fireForTeam(rule, teamId, checkpointId.toString());
            }
        } catch (Exception e) {
            metrics.hookFailed(Hook.CHECKPOINT);
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
            metrics.hookFailed(Hook.GAME_STATUS);
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
                int rivalTotal = pointsTotalsCache.get(rivalId);
                if (oldTotal <= rivalTotal && newTotal > rivalTotal) {
                    for (AutomationRule rule : rules) {
                        fireForTeam(rule, teamId, rivalId.toString());
                    }
                }
            }
        } catch (Exception e) {
            metrics.hookFailed(Hook.OVERTAKE);
            Log.errorf("Automation overtake hook failed for game %s team %s: %s", gameId, teamId, e.getMessage());
        }
    }

    /** A team's accepted fix has a reported speed at or above a rule's threshold. */
    public void onSpeedExceeded(UUID gameId, UUID teamId, double speedKmh) {
        if (EXECUTING.get()) return;
        try {
            for (AutomationRule rule : enabledRules(gameId, AutomationTriggerType.SPEED_ALERT)) {
                if (!rule.appliesToTeam(teamId)) continue;
                Integer threshold = rule.getTriggerConfig() != null
                    ? rule.getTriggerConfig().speedThresholdKmh() : null;
                if (threshold == null || speedKmh < threshold) continue;
                fireForTeam(rule, teamId, null);
            }
        } catch (Exception e) {
            metrics.hookFailed(Hook.SPEED);
            Log.errorf("Automation speed hook failed for game %s team %s: %s", gameId, teamId, e.getMessage());
        }
    }

    /** A team has completed digital check-in. */
    public void onTeamCheckIn(UUID gameId, UUID teamId) {
        if (EXECUTING.get()) return;
        try {
            for (AutomationRule rule : enabledRules(gameId, AutomationTriggerType.TEAM_CHECK_IN)) {
                if (!rule.appliesToTeam(teamId)) continue;
                fireForTeam(rule, teamId, null);
            }
        } catch (Exception e) {
            metrics.hookFailed(Hook.CHECK_IN);
            Log.errorf("Automation check-in hook failed for game %s team %s: %s", gameId, teamId, e.getMessage());
        }
    }

    /**
     * One of a team's uploaded pictures was approved by a moderator. Admin
     * uploads have no owning team and never reach this hook.
     * <p>
     * Rules without a configured threshold fire on every approval; rules with
     * {@code approvedPictureCount} only fire once the team has at least that
     * many approved pictures.
     */
    public void onPictureApproved(UUID gameId, UUID teamId) {
        if (EXECUTING.get()) return;
        try {
            List<AutomationRule> rules = enabledRules(gameId, AutomationTriggerType.PICTURE_APPROVED).stream()
                .filter(rule -> rule.appliesToTeam(teamId))
                .toList();
            if (rules.isEmpty()) return;

            // Counted at most once per approval, and only when a rule needs it.
            Long approvedCount = null;
            for (AutomationRule rule : rules) {
                Integer threshold = rule.getTriggerConfig() != null
                    ? rule.getTriggerConfig().approvedPictureCount() : null;
                if (threshold != null) {
                    if (approvedCount == null) approvedCount = countApprovedPictures(teamId);
                    if (approvedCount < threshold) continue;
                }
                fireForTeam(rule, teamId, null);
            }
        } catch (Exception e) {
            metrics.hookFailed(Hook.PICTURE);
            Log.errorf("Automation picture hook failed for game %s team %s: %s", gameId, teamId, e.getMessage());
        }
    }

    /** A team holds at most a handful of pictures (see vlettenrally.pictures.max-per-team). */
    private long countApprovedPictures(UUID teamId) {
        return pictureRepository.findByTeamId(teamId).stream()
            .filter(picture -> picture.getStatus() == PictureStatus.APPROVED)
            .count();
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
            metrics.hookFailed(Hook.DISTANCE);
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
        // High-water mark: never lower the baseline. The fed value is our own segment-chain
        // sum, which never legitimately resets (a ledger reset deletes this row instead), so
        // a decrease is always a re-measurement — a segment rebuild that shrank the total
        // slightly (jitter-threshold polyline caveat). Lowering the baseline would credit
        // the already-paid meters a second time as the total climbs back past it.
        if (delta > 0) {
            progress.setLastReportedMeters(reportedMeters);
        }

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

        progress.setUpdatedAt(Instant.now());
        distanceProgressRepository.save(progress);
    }

    /**
     * Wipe the distance accrual state for a team across the game's
     * DISTANCE_TRAVELED rules. Called when a team's ledger is reset: the next
     * distance update then re-baselines (first sight, no retroactive credit)
     * and a fresh DISTANCE_BONUS entry is created, instead of accruing onto the
     * now-deleted entry. Exception-safe like the discrete hooks.
     */
    public void resetDistanceProgress(UUID gameId, UUID teamId) {
        try {
            for (AutomationRule rule : ruleRepository.findByGameIdAndTriggerType(
                    gameId, AutomationTriggerType.DISTANCE_TRAVELED)) {
                distanceProgressRepository.deleteByRuleAndTeam(rule.getId(), teamId);
            }
        } catch (Exception e) {
            metrics.hookFailed(Hook.DISTANCE_RESET);
            Log.errorf("Automation distance reset failed for game %s team %s: %s", gameId, teamId, e.getMessage());
        }
    }

    private void creditDistancePoints(AutomationRule rule, UUID teamId,
                                      AutomationDistanceProgress progress, int newPoints) {
        int total = progress.getCreditedPoints() + newPoints;
        Integer pointsPer100m = rule.getTriggerConfig() != null
            ? rule.getTriggerConfig().pointsPer100m() : null;
        // Meters credited this round, derived from the points at the current rate
        // (newPoints is always blocks * pointsPer100m, so this is exact per call).
        double creditedMeters = pointsPer100m != null && pointsPer100m > 0
            ? (double) newPoints / pointsPer100m * DISTANCE_BLOCK_METERS : 0.0;

        // Recreate the entry if it's missing (e.g. deleted by a ledger reset
        // that left this progress row intact) rather than accruing onto a
        // deleted id and crashing the distance hook.
        PointEntry existing = progress.getPointEntryId() != null
            ? pointsLedgerService.findById(progress.getPointEntryId()).orElse(null) : null;
        if (existing == null) {
            // Back-fill meters from the running total so a recreated entry's
            // distance stays consistent with its point value.
            double totalMeters = pointsPer100m != null && pointsPer100m > 0
                ? (double) total / pointsPer100m * DISTANCE_BLOCK_METERS : 0.0;
            PointEntry entry = pointsLedgerService.createEntry(teamId, rule.getGameId(),
                PointEntryType.DISTANCE_BONUS, total, null, "Afstandspunten", null, null, totalMeters);
            progress.setPointEntryId(entry.getId());
        } else {
            double totalMeters = (existing.getDistanceMeters() != null
                ? existing.getDistanceMeters() : 0.0) + creditedMeters;
            pointsLedgerService.accrueEntry(progress.getPointEntryId(), total, totalMeters);
        }
        progress.setCreditedPoints(total);
    }

    /** Scheduler tick: evaluate SCHEDULED_TIME and TEAM_INACTIVITY rules. */
    public void evaluateTimeAndInactivity(Instant now) {
        if (EXECUTING.get()) return;
        evaluateScheduledTime(now);
        evaluateInactivity(now);
    }

    /**
     * Scheduler tick: evaluate SCHEDULED_TIME rules.
     * <p>
     * Deliberately not gated on ACTIVE — a rule anchored to GAME_START with a negative offset
     * is supposed to fire while the game is still PLANNED. Only a paused game is skipped: an
     * emergency stop freezes the schedule too. Because {@link #SCHEDULED_FIRE_WINDOW} is 24h,
     * a rule whose moment passed during the stop fires on resume rather than being lost.
     */
    public void evaluateScheduledTime(Instant now) {
        if (EXECUTING.get()) return;
        for (AutomationRule rule : ruleRepository.findEnabledByTriggerType(AutomationTriggerType.SCHEDULED_TIME)) {
            try {
                Game game = gameService.findById(rule.getGameId()).orElse(null);
                if (game == null || game.getStatus() == Game.GameStatus.PAUSED) continue;

                Instant fireAt = computeFireAt(rule.getTriggerConfig(), game);
                if (fireAt == null || now.isBefore(fireAt)) continue;
                if (fireAt.isBefore(now.minus(SCHEDULED_FIRE_WINDOW))) continue;

                fireGameLevel(rule, game.getId(), null);
            } catch (Exception e) {
                metrics.hookFailed(Hook.SCHEDULED_RULE);
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
            ? game.getStartTime()
            : game.getEndTime();
        return anchor != null ? anchor.plus(Duration.ofMinutes(config.offsetMinutes())) : null;
    }

    /** Scheduler tick: evaluate TEAM_INACTIVITY rules. */
    public void evaluateInactivity(Instant now) {
        if (EXECUTING.get()) return;
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
                        .orElse(game.getStartTime());
                    if (lastSeen == null || lastSeen.isAfter(threshold)) continue;
                    fireForTeam(rule, team.getId(), null);
                }
            } catch (Exception e) {
                metrics.hookFailed(Hook.INACTIVITY_RULE);
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
        SkipReason duplicate = duplicateReason(rule, teamId, contextKey);
        if (duplicate != null) {
            metrics.skipped(duplicate);
            return;
        }
        Team team = teamRepository.findById(teamId).orElse(null);
        if (team == null) {
            metrics.skipped(SkipReason.TEAM_MISSING);
            return;
        }
        if (team.isFinished() && !rule.isTriggerForFinishedTeams()) {
            metrics.skipped(SkipReason.TEAM_FINISHED);
            return;
        }
        if (team.isDisqualified()) {
            metrics.skipped(SkipReason.TEAM_DISQUALIFIED);
            return;
        }
        execute(rule, List.of(team), teamId, contextKey);
    }

    /**
     * Fire a rule at game level (one execution, teamId null), subject to dedup.
     * Actions run against the rule's target teams, or all checked-in teams.
     */
    private void fireGameLevel(AutomationRule rule, UUID gameId, String contextKey) {
        SkipReason duplicate = duplicateReason(rule, null, contextKey);
        if (duplicate != null) {
            metrics.skipped(duplicate);
            return;
        }
        List<Team> targets = teamRepository.findByGameId(gameId).stream()
            .filter(Team::isCheckedIn)
            .filter(team -> !team.isDisqualified())
            .filter(team -> rule.isTriggerForFinishedTeams() || !team.isFinished())
            .filter(team -> rule.appliesToTeam(team.getId()))
            .toList();
        if (targets.isEmpty()) {
            metrics.skipped(SkipReason.NO_TARGETS);
            return;
        }
        execute(rule, targets, null, contextKey);
    }

    /**
     * Why this firing is a duplicate, or {@code null} if it may proceed. Distinguishing
     * fire-once from cooldown matters operationally: the first is permanent, the second
     * self-clears, and "the rule stopped firing" needs to tell them apart.
     */
    private SkipReason duplicateReason(AutomationRule rule, UUID teamId, String contextKey) {
        if (rule.isFireOnce()) {
            return executionRepository.existsByRuleTeamAndContext(rule.getId(), teamId, contextKey)
                ? SkipReason.DUPLICATE : null;
        }
        if (rule.getCooldownMinutes() != null && rule.getCooldownMinutes() > 0) {
            boolean cooling = executionRepository.findLatestFiredAt(rule.getId(), teamId, contextKey)
                .map(last -> last.isAfter(Instant.now().minus(Duration.ofMinutes(rule.getCooldownMinutes()))))
                .orElse(false);
            return cooling ? SkipReason.COOLDOWN : null;
        }
        return null;
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

        metrics.fired(rule.getTriggerType(), result.status());
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
        webSocketPublisher.sendToAdminTopic(rule.getGameId(), AdminTopics.AUTOMATIONS,
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
