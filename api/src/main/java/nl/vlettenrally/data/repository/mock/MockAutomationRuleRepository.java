package nl.vlettenrally.data.repository.mock;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import nl.vlettenrally.data.domain.AutomationRule;
import nl.vlettenrally.data.domain.automation.AutomationAction;
import nl.vlettenrally.data.domain.automation.AutomationTriggerConfig;
import nl.vlettenrally.data.enums.AutomationActionType;
import nl.vlettenrally.data.enums.AutomationTriggerType;
import nl.vlettenrally.data.enums.MessageType;
import nl.vlettenrally.data.repository.AutomationRuleRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock implementation of AutomationRuleRepository with prefilled data.
 * Only active in dev and test profiles - replaced by JpaAutomationRuleRepository in prod.
 */
@IfBuildProfile(anyOf = {"dev", "test"})
@ApplicationScoped
public class MockAutomationRuleRepository implements AutomationRuleRepository {

    public static final UUID RULE_DISTANCE_ID = UUID.fromString("d0000001-0000-0000-0000-000000000001");
    public static final UUID RULE_CHECKPOINT_MESSAGE_ID = UUID.fromString("d0000002-0000-0000-0000-000000000002");
    public static final UUID RULE_INACTIVITY_ALERT_ID = UUID.fromString("d0000003-0000-0000-0000-000000000003");
    public static final UUID RULE_LATE_FINISH_ID = UUID.fromString("d0000004-0000-0000-0000-000000000004");

    private final Map<UUID, AutomationRule> rules = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        initializeData();
    }

    public void initializeData() {
        rules.clear();

        UUID game = MockGameRepository.GAME_2026_ID;

        // The built-in system rule every game carries: 1 point per 100 m.
        AutomationRule distance = new AutomationRule();
        distance.setId(RULE_DISTANCE_ID);
        distance.setGameId(game);
        distance.setName("Afstandspunten");
        distance.setSystem(true);
        distance.setTriggerType(AutomationTriggerType.DISTANCE_TRAVELED);
        distance.setTriggerConfig(new AutomationTriggerConfig(null, null, null, null, null, null, null, 1, null));
        distance.setFireOnce(false);
        rules.put(distance.getId(), distance);

        AutomationRule checkpointMessage = new AutomationRule();
        checkpointMessage.setId(RULE_CHECKPOINT_MESSAGE_ID);
        checkpointMessage.setGameId(game);
        checkpointMessage.setName("Felicitatie bij checkpoint");
        checkpointMessage.setTriggerType(AutomationTriggerType.CHECKPOINT_ENTRY);
        checkpointMessage.setTriggerConfig(new AutomationTriggerConfig(null, null, null, null, null, null, null, null, null));
        checkpointMessage.setActions(List.of(new AutomationAction(
            AutomationActionType.SEND_MESSAGE,
            "Goed bezig {team}, checkpoint bereikt!", MessageType.GENERAL, "🎉",
            null, null, null, null)));
        rules.put(checkpointMessage.getId(), checkpointMessage);

        AutomationRule inactivityAlert = new AutomationRule();
        inactivityAlert.setId(RULE_INACTIVITY_ALERT_ID);
        inactivityAlert.setGameId(game);
        inactivityAlert.setName("Stilstand-melding");
        inactivityAlert.setTriggerType(AutomationTriggerType.TEAM_INACTIVITY);
        inactivityAlert.setTriggerConfig(new AutomationTriggerConfig(null, null, null, null, null, null, 30, null, null));
        inactivityAlert.setActions(List.of(new AutomationAction(
            AutomationActionType.NOTIFY_ADMINS,
            null, null, null, null, null, null,
            "{team} heeft al 30 minuten geen locatie doorgegeven")));
        inactivityAlert.setFireOnce(false);
        inactivityAlert.setCooldownMinutes(60);
        rules.put(inactivityAlert.getId(), inactivityAlert);

        // Finish-zone penalty: arriving at the finish after the game's end time
        // costs points (pass time is compared against the boundary).
        AutomationRule latePenalty = new AutomationRule();
        latePenalty.setId(RULE_LATE_FINISH_ID);
        latePenalty.setGameId(game);
        latePenalty.setName("Te laat gefinisht");
        latePenalty.setTriggerType(AutomationTriggerType.CHECKPOINT_ENTRY);
        latePenalty.setTriggerConfig(new AutomationTriggerConfig(
            MockCheckpointRepository.CHECKPOINT_FINISH_ID,
            AutomationTriggerConfig.ScheduleMode.RELATIVE, null,
            AutomationTriggerConfig.RelativeAnchor.GAME_END, 0,
            null, null, null,
            AutomationTriggerConfig.PassComparison.AFTER));
        latePenalty.setActions(List.of(new AutomationAction(
            AutomationActionType.AWARD_POINTS,
            null, null, null, null, -50, "Strafpunten: na de eindtijd gefinisht", null)));
        rules.put(latePenalty.getId(), latePenalty);
    }

    @Override
    public Optional<AutomationRule> findById(UUID id) {
        return Optional.ofNullable(rules.get(id));
    }

    @Override
    public List<AutomationRule> findByGameId(UUID gameId) {
        return rules.values().stream()
            .filter(r -> gameId.equals(r.getGameId()))
            .sorted(Comparator.comparing(AutomationRule::getCreatedAt).reversed())
            .toList();
    }

    @Override
    public List<AutomationRule> findByGameIdAndTriggerType(UUID gameId, AutomationTriggerType triggerType) {
        return rules.values().stream()
            .filter(r -> gameId.equals(r.getGameId()) && r.getTriggerType() == triggerType)
            .toList();
    }

    @Override
    public List<AutomationRule> findEnabledByTriggerType(AutomationTriggerType triggerType) {
        return rules.values().stream()
            .filter(r -> r.isEnabled() && r.getTriggerType() == triggerType)
            .toList();
    }

    @Override
    public AutomationRule save(AutomationRule rule) {
        rules.put(rule.getId(), rule);
        return rule;
    }

    @Override
    public void delete(UUID id) {
        rules.remove(id);
    }
}
