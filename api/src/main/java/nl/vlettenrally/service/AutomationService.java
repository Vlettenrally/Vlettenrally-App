package nl.vlettenrally.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import nl.vlettenrally.data.domain.*;
import nl.vlettenrally.data.domain.automation.AutomationAction;
import nl.vlettenrally.data.domain.automation.AutomationTriggerConfig;
import nl.vlettenrally.data.dto.automation.CopyAutomationsResult;
import nl.vlettenrally.data.enums.AutomationTriggerType;
import nl.vlettenrally.data.repository.AutomationExecutionRepository;
import nl.vlettenrally.data.repository.AutomationRuleRepository;
import nl.vlettenrally.data.repository.CheckpointRepository;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.service.util.GameTimeShifter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CRUD and validation for automation rules.
 * <p>
 * System rules (the per-game "Afstandspunten" distance rule) cannot be deleted
 * and their trigger type is fixed; DISTANCE_TRAVELED rules cannot be created
 * by hand (one per game, created with the game — prevents double-crediting).
 */
@ApplicationScoped
public class AutomationService {

    public static final String SYSTEM_DISTANCE_RULE_NAME = "Afstandspunten";
    public static final int DEFAULT_POINTS_PER_100M = 1;

    /** A copied checkpoint rule matches a target checkpoint within this distance. */
    private static final double CHECKPOINT_MATCH_TOLERANCE_METERS = 50.0;
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    @Inject
    AutomationRuleRepository ruleRepository;

    @Inject
    AutomationExecutionRepository executionRepository;

    @Inject
    TeamRepository teamRepository;

    @Inject
    CheckpointRepository checkpointRepository;

    @Inject
    GameService gameService;

    @Inject
    MessageTemplateService messageTemplateService;

    public List<AutomationRule> listRules(UUID gameId) {
        return ruleRepository.findByGameId(gameId);
    }

    public AutomationRule getRule(UUID gameId, UUID ruleId) {
        return ruleRepository.findById(ruleId)
            .filter(rule -> gameId.equals(rule.getGameId()))
            .orElseThrow(() -> new NotFoundException("Automation rule not found: " + ruleId));
    }

    public AutomationRule createRule(UUID gameId, AutomationRule draft, UUID adminId) {
        if (draft.getTriggerType() == AutomationTriggerType.DISTANCE_TRAVELED) {
            throw new BadRequestException(
                "Distance rules cannot be created by hand; every game already has its system rule");
        }
        draft.setGameId(gameId);
        draft.setSystem(false);
        draft.setCreatedBy(adminId);
        validate(draft);
        return ruleRepository.save(draft);
    }

    public AutomationRule updateRule(UUID gameId, UUID ruleId, AutomationRule draft) {
        AutomationRule existing = getRule(gameId, ruleId);

        if (existing.isSystem() && draft.getTriggerType() != existing.getTriggerType()) {
            throw new BadRequestException("The trigger of a system rule cannot be changed");
        }
        if (!existing.isSystem() && draft.getTriggerType() == AutomationTriggerType.DISTANCE_TRAVELED) {
            throw new BadRequestException("A rule cannot be changed into a distance rule");
        }

        existing.setName(draft.getName());
        // System rules' enabled flag is lifecycle-managed; ignore the draft value.
        if (!existing.isSystem()) {
            existing.setEnabled(draft.isEnabled());
        }
        existing.setTriggerType(draft.getTriggerType());
        existing.setTriggerConfig(draft.getTriggerConfig());
        existing.setActions(draft.getActions());
        existing.setTargetTeamIds(draft.getTargetTeamIds());
        existing.setFireOnce(draft.isFireOnce());
        existing.setCooldownMinutes(draft.getCooldownMinutes());
        existing.setUpdatedAt(Instant.now());
        validate(existing);
        return ruleRepository.save(existing);
    }

    public void deleteRule(UUID gameId, UUID ruleId) {
        AutomationRule rule = getRule(gameId, ruleId);
        if (rule.isSystem()) {
            throw new BadRequestException("System rules cannot be deleted");
        }
        ruleRepository.delete(ruleId);
    }

    public AutomationRule setEnabled(UUID gameId, UUID ruleId, boolean enabled) {
        AutomationRule rule = getRule(gameId, ruleId);
        if (rule.isSystem()) {
            throw new BadRequestException(
                "The distance rule is enabled automatically with the game status");
        }
        rule.setEnabled(enabled);
        rule.setUpdatedAt(Instant.now());
        return ruleRepository.save(rule);
    }

    public List<AutomationExecution> listExecutions(UUID gameId, UUID ruleId, int page, int pageSize) {
        getRule(gameId, ruleId);
        return executionRepository.findByRuleId(ruleId, page, pageSize);
    }

    public long countExecutions(UUID gameId, UUID ruleId) {
        getRule(gameId, ruleId);
        return executionRepository.countByRuleId(ruleId);
    }

    public Map<UUID, Instant> lastFiredAt(Set<UUID> ruleIds) {
        return executionRepository.findLatestFiredAtByRuleIds(ruleIds);
    }

    public long executionCount(UUID ruleId) {
        return executionRepository.countByRuleId(ruleId);
    }

    /**
     * Create the built-in distance rule for a newly created game: enabled,
     * {@value DEFAULT_POINTS_PER_100M} point(s) per 100 m traveled.
     */
    public AutomationRule createSystemDistanceRule(UUID gameId) {
        AutomationRule rule = new AutomationRule();
        rule.setGameId(gameId);
        rule.setName(SYSTEM_DISTANCE_RULE_NAME);
        rule.setSystem(true);
        rule.setTriggerType(AutomationTriggerType.DISTANCE_TRAVELED);
        rule.setTriggerConfig(new AutomationTriggerConfig(null, null, null, null, null, null, null, DEFAULT_POINTS_PER_100M, null));
        rule.setFireOnce(false);
        return ruleRepository.save(rule);
    }

    /**
     * Copy all eligible (non-system) automation rules from {@code sourceGameId}
     * into {@code targetGameId}, and copy the source distance-points rate onto
     * the target's system rule.
     * <p>
     * Game-specific references are remapped: {@code targetTeamIds} are dropped
     * (the copy applies to all teams), CHECKPOINT_ENTRY checkpoints are matched
     * to the target game by external id / location / name (unmatched rules are
     * skipped and reported), and absolute schedule times are shifted into the
     * target game's timeframe. Rules whose name already exists in the target are
     * skipped, so re-copying is idempotent.
     */
    public CopyAutomationsResult copyRules(UUID targetGameId, UUID sourceGameId, UUID adminId) {
        if (targetGameId.equals(sourceGameId)) {
            throw new BadRequestException("Cannot copy automations from a game into itself");
        }

        Game sourceGame = gameService.findById(sourceGameId)
            .orElseThrow(() -> new NotFoundException("Source game not found: " + sourceGameId));
        Game targetGame = gameService.findById(targetGameId)
            .orElseThrow(() -> new NotFoundException("Target game not found: " + targetGameId));

        List<AutomationRule> sourceRules = ruleRepository.findByGameId(sourceGameId);
        List<Checkpoint> targetCheckpoints = checkpointRepository.findByGameId(targetGameId);
        Set<String> existingNames = ruleRepository.findByGameId(targetGameId).stream()
            .map(rule -> rule.getName().toLowerCase(Locale.ROOT))
            .collect(Collectors.toCollection(HashSet::new));

        int copied = 0;
        List<String> skippedDuplicate = new ArrayList<>();
        List<String> skippedUnmatchedCheckpoint = new ArrayList<>();

        for (AutomationRule source : sourceRules) {
            if (source.isSystem()) {
                continue; // the distance rule's rate is handled separately
            }
            if (!existingNames.add(source.getName().toLowerCase(Locale.ROOT))) {
                skippedDuplicate.add(source.getName());
                continue;
            }

            AutomationTriggerConfig config = source.getTriggerConfig();

            // Remap a checkpoint-specific reference to the target game.
            UUID newCheckpointId = config != null ? config.checkpointId() : null;
            if (source.getTriggerType() == AutomationTriggerType.CHECKPOINT_ENTRY && newCheckpointId != null) {
                Optional<Checkpoint> match = matchCheckpoint(newCheckpointId, targetCheckpoints);
                if (match.isEmpty()) {
                    existingNames.remove(source.getName().toLowerCase(Locale.ROOT));
                    skippedUnmatchedCheckpoint.add(source.getName());
                    continue;
                }
                newCheckpointId = match.get().getId();
            }

            // Shift an absolute schedule/finish time into the target timeframe.
            Instant newAbsolute = config != null ? config.absoluteTime() : null;
            if (config != null && config.mode() == AutomationTriggerConfig.ScheduleMode.ABSOLUTE
                    && newAbsolute != null) {
                newAbsolute = GameTimeShifter.adjust(newAbsolute, sourceGame, targetGame);
            }

            AutomationRule draft = new AutomationRule();
            draft.setName(source.getName());
            draft.setEnabled(source.isEnabled());
            draft.setTriggerType(source.getTriggerType());
            draft.setTriggerConfig(config == null ? null : new AutomationTriggerConfig(
                newCheckpointId, config.mode(), newAbsolute, config.relativeTo(),
                config.offsetMinutes(), config.toStatus(), config.inactiveMinutes(),
                config.pointsPer100m(), config.comparison()));
            draft.setActions(new ArrayList<>(source.getActions()));
            draft.setTargetTeamIds(new ArrayList<>()); // team ids are game-specific
            draft.setFireOnce(source.isFireOnce());
            draft.setCooldownMinutes(source.getCooldownMinutes());

            createRule(targetGameId, draft, adminId);
            copied++;
        }

        boolean distanceRateCopied = copyDistanceRate(sourceGameId, targetGameId);
        return new CopyAutomationsResult(copied, skippedDuplicate, skippedUnmatchedCheckpoint, distanceRateCopied);
    }

    /**
     * Find the target-game checkpoint equivalent to a source checkpoint:
     * exact external id, else nearest within {@link #CHECKPOINT_MATCH_TOLERANCE_METERS},
     * else case-insensitive name.
     */
    private Optional<Checkpoint> matchCheckpoint(UUID sourceCheckpointId, List<Checkpoint> targetCheckpoints) {
        Checkpoint source = checkpointRepository.findById(sourceCheckpointId).orElse(null);
        if (source == null) {
            return Optional.empty();
        }

        if (source.getExternalId() != null && !source.getExternalId().isBlank()) {
            Optional<Checkpoint> byExternal = targetCheckpoints.stream()
                .filter(cp -> source.getExternalId().equals(cp.getExternalId()))
                .findFirst();
            if (byExternal.isPresent()) {
                return byExternal;
            }
        }

        if (source.getLatitude() != null && source.getLongitude() != null) {
            Checkpoint nearest = null;
            double nearestMeters = Double.MAX_VALUE;
            for (Checkpoint cp : targetCheckpoints) {
                if (cp.getLatitude() == null || cp.getLongitude() == null) {
                    continue;
                }
                double meters = haversineMeters(
                    source.getLatitude(), source.getLongitude(), cp.getLatitude(), cp.getLongitude());
                if (meters < nearestMeters) {
                    nearestMeters = meters;
                    nearest = cp;
                }
            }
            if (nearest != null && nearestMeters <= CHECKPOINT_MATCH_TOLERANCE_METERS) {
                return Optional.of(nearest);
            }
        }

        return targetCheckpoints.stream()
            .filter(cp -> cp.getName() != null && cp.getName().equalsIgnoreCase(source.getName()))
            .findFirst();
    }

    /** Apply the source game's distance-points rate and enabled flag to the target's system rule. */
    private boolean copyDistanceRate(UUID sourceGameId, UUID targetGameId) {
        AutomationRule source = systemDistanceRule(sourceGameId);
        AutomationRule target = systemDistanceRule(targetGameId);
        if (source == null || target == null) {
            return false;
        }
        Integer rate = source.getTriggerConfig() != null ? source.getTriggerConfig().pointsPer100m() : null;
        if (rate == null) {
            return false;
        }
        target.setTriggerConfig(new AutomationTriggerConfig(null, null, null, null, null, null, null, rate, null));
        // enabled stays driven by the target game's own status, not the source's.
        target.setUpdatedAt(Instant.now());
        ruleRepository.save(target);
        return true;
    }

    private AutomationRule systemDistanceRule(UUID gameId) {
        return ruleRepository.findByGameIdAndTriggerType(gameId, AutomationTriggerType.DISTANCE_TRAVELED).stream()
            .filter(AutomationRule::isSystem)
            .findFirst()
            .orElse(null);
    }

    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private void validate(AutomationRule rule) {
        if (rule.getName() == null || rule.getName().isBlank()) {
            throw new BadRequestException("Rule name is required");
        }
        if (rule.getTriggerType() == null) {
            throw new BadRequestException("Trigger type is required");
        }
        if (rule.isFireOnce() && rule.getCooldownMinutes() != null) {
            throw new BadRequestException("Cooldown only applies when fire-once is off");
        }
        if (rule.getCooldownMinutes() != null && rule.getCooldownMinutes() <= 0) {
            throw new BadRequestException("Cooldown must be positive");
        }
        validateTriggerConfig(rule);
        validateActions(rule);
        validateTargetTeams(rule);
    }

    private void validateTriggerConfig(AutomationRule rule) {
        AutomationTriggerConfig config = rule.getTriggerConfig();
        switch (rule.getTriggerType()) {
            case CHECKPOINT_ENTRY -> {
                UUID checkpointId = config != null ? config.checkpointId() : null;
                if (checkpointId != null && checkpointRepository.findById(checkpointId)
                        .filter(cp -> rule.getGameId().equals(cp.getGameId())).isEmpty()) {
                    throw new BadRequestException("Checkpoint does not belong to this game: " + checkpointId);
                }
                if (config != null && config.comparison() != null) {
                    validateTimeSpec(config, "Checkpoint rules with a before/after condition");
                }
            }
            case SCHEDULED_TIME -> validateTimeSpec(config, "Scheduled rules");
            case GAME_STATUS_CHANGE -> {
                if (config == null || config.toStatus() == null) {
                    throw new BadRequestException("Status rules need a target status");
                }
            }
            case TEAM_INACTIVITY -> {
                if (config == null || config.inactiveMinutes() == null || config.inactiveMinutes() <= 0) {
                    throw new BadRequestException("Inactivity rules need a positive number of minutes");
                }
            }
            case RIVAL_OVERTAKE -> { /* no config */ }
            case DISTANCE_TRAVELED -> {
                if (config == null || config.pointsPer100m() == null || config.pointsPer100m() <= 0) {
                    throw new BadRequestException("Distance rules need a positive points-per-100m");
                }
            }
        }
    }

    /** The ABSOLUTE/RELATIVE time spec shared by scheduled rules and checkpoint time windows. */
    private static void validateTimeSpec(AutomationTriggerConfig config, String subject) {
        if (config == null || config.mode() == null) {
            throw new BadRequestException(subject + " need a mode (ABSOLUTE or RELATIVE)");
        }
        if (config.mode() == AutomationTriggerConfig.ScheduleMode.ABSOLUTE
                && config.absoluteTime() == null) {
            throw new BadRequestException(subject + " need a time");
        }
        if (config.mode() == AutomationTriggerConfig.ScheduleMode.RELATIVE
                && (config.relativeTo() == null || config.offsetMinutes() == null)) {
            throw new BadRequestException(subject + " need an anchor and offset");
        }
    }

    private void validateActions(AutomationRule rule) {
        if (rule.getTriggerType() == AutomationTriggerType.DISTANCE_TRAVELED) {
            return; // crediting is intrinsic; the actions list stays empty
        }
        if (rule.getActions() == null || rule.getActions().isEmpty()) {
            throw new BadRequestException("At least one action is required");
        }
        for (AutomationAction action : rule.getActions()) {
            if (action.type() == null) {
                throw new BadRequestException("Every action needs a type");
            }
            switch (action.type()) {
                case SEND_MESSAGE -> {
                    boolean hasContent = action.messageContent() != null && !action.messageContent().isBlank();
                    if (action.messageTemplateId() != null) {
                        if (messageTemplateService.findById(action.messageTemplateId()).isEmpty()) {
                            throw new BadRequestException(
                                "Message template not found: " + action.messageTemplateId());
                        }
                    } else if (!hasContent) {
                        throw new BadRequestException("Message actions need content or a template");
                    }
                }
                case AWARD_POINTS -> {
                    if (action.points() == null || action.points() == 0) {
                        throw new BadRequestException("Point actions need a non-zero amount");
                    }
                }
                case NOTIFY_ADMINS -> {
                    if (action.adminNotification() == null || action.adminNotification().isBlank()) {
                        throw new BadRequestException("Admin notification actions need a text");
                    }
                }
            }
        }
    }

    private void validateTargetTeams(AutomationRule rule) {
        if (rule.getTargetTeamIds() == null || rule.getTargetTeamIds().isEmpty()) {
            return;
        }
        Set<UUID> gameTeamIds = teamRepository.findByGameId(rule.getGameId()).stream()
            .map(Team::getId)
            .collect(Collectors.toSet());
        for (UUID teamId : rule.getTargetTeamIds()) {
            if (!gameTeamIds.contains(teamId)) {
                throw new BadRequestException("Team does not belong to this game: " + teamId);
            }
        }
    }
}
