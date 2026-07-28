package nl.vlettenrally.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nl.vlettenrally.data.domain.AutomationRule;
import nl.vlettenrally.data.domain.Checkpoint;
import nl.vlettenrally.data.domain.CircleCheckpoint;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.domain.automation.AutomationAction;
import nl.vlettenrally.data.domain.automation.AutomationTriggerConfig;
import nl.vlettenrally.data.dto.automation.CopyAutomationsResult;
import nl.vlettenrally.data.enums.AutomationTriggerType;
import nl.vlettenrally.data.repository.AutomationExecutionRepository;
import nl.vlettenrally.data.repository.AutomationRuleRepository;
import nl.vlettenrally.data.repository.CheckpointRepository;
import nl.vlettenrally.data.repository.TeamRepository;

/**
 * Unit tests for AutomationService: per-trigger config validation, action
 * validation, target-team scoping, and the system-rule guards.
 */
@ExtendWith(MockitoExtension.class)
class AutomationServiceTest {

    @Mock AutomationRuleRepository ruleRepository;
    @Mock AutomationExecutionRepository executionRepository;
    @Mock TeamRepository teamRepository;
    @Mock CheckpointRepository checkpointRepository;
    @Mock GameService gameService;
    @Mock MessageTemplateService messageTemplateService;

    @InjectMocks AutomationService service;

    private UUID gameId;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        gameId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        lenient().when(ruleRepository.save(any(AutomationRule.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private AutomationRule validDraft() {
        AutomationRule draft = new AutomationRule();
        draft.setName("Felicitatie");
        draft.setTriggerType(AutomationTriggerType.CHECKPOINT_ENTRY);
        draft.setTriggerConfig(new AutomationTriggerConfig(null, null, null, null, null, null, null, null, null));
        draft.setActions(List.of(new AutomationAction.SendMessage("Goed bezig!", null, null, null)));
        return draft;
    }

    // ==================== create ====================

    @Test
    void createRule_persistsValidRule_withCreator() {
        AutomationRule created = service.createRule(gameId, validDraft(), adminId);

        assertEquals(gameId, created.getGameId());
        assertEquals(adminId, created.getCreatedBy());
        assertFalse(created.isSystem());
        verify(ruleRepository).save(created);
    }

    @Test
    void createRule_acceptsFinishTeamAction() {
        AutomationRule draft = validDraft();
        draft.setActions(List.of(new AutomationAction.FinishTeam()));

        AutomationRule created = service.createRule(gameId, draft, adminId);

        assertEquals(gameId, created.getGameId());
        verify(ruleRepository).save(created);
    }

    @Test
    void createRule_rejectsDistanceTrigger() {
        AutomationRule draft = validDraft();
        draft.setTriggerType(AutomationTriggerType.DISTANCE_TRAVELED);

        assertThrows(BadRequestException.class, () -> service.createRule(gameId, draft, adminId));
        verify(ruleRepository, never()).save(any());
    }

    @Test
    void createRule_rejectsBlankName() {
        AutomationRule draft = validDraft();
        draft.setName("  ");

        assertThrows(BadRequestException.class, () -> service.createRule(gameId, draft, adminId));
    }

    @Test
    void createRule_rejectsEmptyActions() {
        AutomationRule draft = validDraft();
        draft.setActions(List.of());

        assertThrows(BadRequestException.class, () -> service.createRule(gameId, draft, adminId));
    }

    @Test
    void createRule_rejectsCooldownWithFireOnce() {
        AutomationRule draft = validDraft();
        draft.setFireOnce(true);
        draft.setCooldownMinutes(60);

        assertThrows(BadRequestException.class, () -> service.createRule(gameId, draft, adminId));
    }

    @Test
    void createRule_rejectsForeignCheckpoint() {
        UUID checkpointId = UUID.randomUUID();
        CircleCheckpoint checkpoint = new CircleCheckpoint();
        checkpoint.setId(checkpointId);
        checkpoint.setGameId(UUID.randomUUID()); // other game
        when(checkpointRepository.findById(checkpointId)).thenReturn(Optional.of(checkpoint));

        AutomationRule draft = validDraft();
        draft.setTriggerConfig(new AutomationTriggerConfig(checkpointId, null, null, null, null, null, null, null, null));

        assertThrows(BadRequestException.class, () -> service.createRule(gameId, draft, adminId));
    }

    @Test
    void createRule_rejectsForeignExcludedCheckpoint() {
        UUID excludedId = UUID.randomUUID();
        CircleCheckpoint checkpoint = new CircleCheckpoint();
        checkpoint.setId(excludedId);
        checkpoint.setGameId(UUID.randomUUID()); // other game
        when(checkpointRepository.findById(excludedId)).thenReturn(Optional.of(checkpoint));

        AutomationRule draft = validDraft();
        draft.setTriggerConfig(new AutomationTriggerConfig(null, null, null, null, null, null, null, null, null,
            List.of(excludedId)));

        assertThrows(BadRequestException.class, () -> service.createRule(gameId, draft, adminId));
    }

    @Test
    void createRule_rejectsForeignCheckpointInSet() {
        UUID foreignId = UUID.randomUUID();
        CircleCheckpoint checkpoint = new CircleCheckpoint();
        checkpoint.setId(foreignId);
        checkpoint.setGameId(UUID.randomUUID()); // other game
        when(checkpointRepository.findById(foreignId)).thenReturn(Optional.of(checkpoint));

        AutomationRule draft = validDraft();
        draft.setTriggerConfig(new AutomationTriggerConfig(null, null, null, null, null, null, null, null, null,
            null, null, List.of(foreignId)));

        assertThrows(BadRequestException.class, () -> service.createRule(gameId, draft, adminId));
    }

    @Test
    void createRule_rejectsForeignTargetTeam() {
        Team ownTeam = new Team();
        ownTeam.setId(UUID.randomUUID());
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(ownTeam));

        AutomationRule draft = validDraft();
        draft.setTargetTeamIds(List.of(UUID.randomUUID()));

        assertThrows(BadRequestException.class, () -> service.createRule(gameId, draft, adminId));
    }

    @Test
    void createRule_rejectsIncompleteScheduledConfig() {
        AutomationRule draft = validDraft();
        draft.setTriggerType(AutomationTriggerType.SCHEDULED_TIME);
        draft.setTriggerConfig(new AutomationTriggerConfig(null, AutomationTriggerConfig.ScheduleMode.RELATIVE, null, null, null, null, null, null, null));

        assertThrows(BadRequestException.class, () -> service.createRule(gameId, draft, adminId));
    }

    @Test
    void createRule_rejectsCheckpointTimeWindowWithoutTimeSpec() {
        AutomationRule draft = validDraft();
        // BEFORE comparison set, but no mode/time configured.
        draft.setTriggerConfig(new AutomationTriggerConfig(null, null, null, null, null, null, null, null,
            AutomationTriggerConfig.PassComparison.BEFORE));

        assertThrows(BadRequestException.class, () -> service.createRule(gameId, draft, adminId));
    }

    @Test
    void createRule_acceptsCheckpointTimeWindowWithRelativeSpec() {
        AutomationRule draft = validDraft();
        draft.setTriggerConfig(new AutomationTriggerConfig(null,
            AutomationTriggerConfig.ScheduleMode.RELATIVE, null,
            AutomationTriggerConfig.RelativeAnchor.GAME_END, 0, null, null, null,
            AutomationTriggerConfig.PassComparison.AFTER));

        AutomationRule created = service.createRule(gameId, draft, adminId);

        assertEquals(AutomationTriggerConfig.PassComparison.AFTER,
            created.getTriggerConfig().comparison());
    }

    @Test
    void createRule_acceptsTeamCheckIn_withNoConfig() {
        AutomationRule draft = validDraft();
        draft.setTriggerType(AutomationTriggerType.TEAM_CHECK_IN);
        draft.setTriggerConfig(new AutomationTriggerConfig(null, null, null, null, null, null, null, null, null));

        AutomationRule created = service.createRule(gameId, draft, adminId);

        assertEquals(AutomationTriggerType.TEAM_CHECK_IN, created.getTriggerType());
        verify(ruleRepository).save(created);
    }

    /** The approved-picture threshold is optional: no config means every approval fires. */
    @Test
    void createRule_acceptsPictureApproved_withoutThreshold() {
        AutomationRule draft = validDraft();
        draft.setTriggerType(AutomationTriggerType.PICTURE_APPROVED);
        draft.setTriggerConfig(new AutomationTriggerConfig(null, null, null, null, null, null, null, null, null));

        AutomationRule created = service.createRule(gameId, draft, adminId);

        assertEquals(AutomationTriggerType.PICTURE_APPROVED, created.getTriggerType());
        verify(ruleRepository).save(created);
    }

    @Test
    void createRule_acceptsPictureApproved_withPositiveThreshold() {
        AutomationRule draft = validDraft();
        draft.setTriggerType(AutomationTriggerType.PICTURE_APPROVED);
        draft.setTriggerConfig(new AutomationTriggerConfig(null, null, null, null, null, null, null, null, null,
            null, null, null, 3));

        AutomationRule created = service.createRule(gameId, draft, adminId);

        assertEquals(3, created.getTriggerConfig().approvedPictureCount());
    }

    @Test
    void createRule_rejectsPictureApproved_withNonPositiveThreshold() {
        AutomationRule draft = validDraft();
        draft.setTriggerType(AutomationTriggerType.PICTURE_APPROVED);
        draft.setTriggerConfig(new AutomationTriggerConfig(null, null, null, null, null, null, null, null, null,
            null, null, null, 0));

        assertThrows(BadRequestException.class, () -> service.createRule(gameId, draft, adminId));
    }

    @Test
    void createRule_rejectsInactivityWithoutMinutes() {
        AutomationRule draft = validDraft();
        draft.setTriggerType(AutomationTriggerType.TEAM_INACTIVITY);
        draft.setTriggerConfig(new AutomationTriggerConfig(null, null, null, null, null, null, null, null, null));

        assertThrows(BadRequestException.class, () -> service.createRule(gameId, draft, adminId));
    }

    @Test
    void createRule_acceptsSendMessageWithValidTemplate() {
        UUID templateId = UUID.randomUUID();
        when(messageTemplateService.findById(templateId)).thenReturn(Optional.of(
            new nl.vlettenrally.data.domain.MessageTemplate(templateId, "T",
                nl.vlettenrally.data.enums.MessageType.GENERAL, "Hoi {team}", null)));
        AutomationRule draft = validDraft();
        draft.setActions(List.of(new AutomationAction.SendMessage(null, null, null, templateId)));

        AutomationRule created = service.createRule(gameId, draft, adminId);

        assertEquals(templateId, ((AutomationAction.SendMessage) created.getActions().getFirst()).messageTemplateId());
    }

    @Test
    void createRule_rejectsSendMessageWithUnknownTemplate() {
        UUID templateId = UUID.randomUUID();
        when(messageTemplateService.findById(templateId)).thenReturn(Optional.empty());
        AutomationRule draft = validDraft();
        draft.setActions(List.of(new AutomationAction.SendMessage(null, null, null, templateId)));

        assertThrows(BadRequestException.class, () -> service.createRule(gameId, draft, adminId));
    }

    @Test
    void createRule_rejectsSendMessageWithNeitherTemplateNorContent() {
        AutomationRule draft = validDraft();
        draft.setActions(List.of(new AutomationAction.SendMessage("  ", null, null, null)));

        assertThrows(BadRequestException.class, () -> service.createRule(gameId, draft, adminId));
    }

    @Test
    void createRule_rejectsZeroPointsAction() {
        AutomationRule draft = validDraft();
        draft.setActions(List.of(new AutomationAction.AwardPoints(0, null)));

        assertThrows(BadRequestException.class, () -> service.createRule(gameId, draft, adminId));
    }

    @Test
    void createRule_acceptsSetRankingsPublished_withExplicitValue() {
        AutomationRule draft = validDraft();
        draft.setActions(List.of(new AutomationAction.SetRankingsPublished(true)));

        AutomationRule created = service.createRule(gameId, draft, adminId);

        assertTrue(((AutomationAction.SetRankingsPublished) created.getActions().getFirst()).rankingsPublished());
    }

    @Test
    void createRule_rejectsSetRankingsPublished_withoutValue() {
        AutomationRule draft = validDraft();
        draft.setActions(List.of(new AutomationAction.SetRankingsPublished(null)));

        assertThrows(BadRequestException.class, () -> service.createRule(gameId, draft, adminId));
    }

    @Test
    void createRule_acceptsSetPublicRankingsEnabled_withExplicitValue() {
        AutomationRule draft = validDraft();
        draft.setActions(List.of(new AutomationAction.SetPublicRankingsEnabled(true)));

        AutomationRule created = service.createRule(gameId, draft, adminId);

        assertTrue(((AutomationAction.SetPublicRankingsEnabled) created.getActions().getFirst()).publicRankingsEnabled());
    }

    @Test
    void createRule_rejectsSetPublicRankingsEnabled_withoutValue() {
        AutomationRule draft = validDraft();
        draft.setActions(List.of(new AutomationAction.SetPublicRankingsEnabled(null)));

        assertThrows(BadRequestException.class, () -> service.createRule(gameId, draft, adminId));
    }

    // ==================== update / delete / system guards ====================

    private AutomationRule existingSystemRule() {
        AutomationRule rule = new AutomationRule();
        rule.setGameId(gameId);
        rule.setName(AutomationService.SYSTEM_DISTANCE_RULE_NAME);
        rule.setSystem(true);
        rule.setTriggerType(AutomationTriggerType.DISTANCE_TRAVELED);
        rule.setTriggerConfig(new AutomationTriggerConfig(null, null, null, null, null, null, null, 1, null));
        rule.setFireOnce(false);
        when(ruleRepository.findById(rule.getId())).thenReturn(Optional.of(rule));
        return rule;
    }

    @Test
    void updateRule_rejectsTriggerChangeOnSystemRule() {
        AutomationRule existing = existingSystemRule();
        AutomationRule draft = validDraft();

        assertThrows(BadRequestException.class,
            () -> service.updateRule(gameId, existing.getId(), draft));
    }

    @Test
    void updateRule_allowsRateChangeOnSystemRule() {
        AutomationRule existing = existingSystemRule();
        AutomationRule draft = new AutomationRule();
        draft.setName("Afstandspunten");
        draft.setTriggerType(AutomationTriggerType.DISTANCE_TRAVELED);
        draft.setTriggerConfig(new AutomationTriggerConfig(null, null, null, null, null, null, null, 2, null));
        draft.setFireOnce(false);

        AutomationRule updated = service.updateRule(gameId, existing.getId(), draft);

        assertEquals(2, updated.getTriggerConfig().pointsPer100m());
        assertTrue(updated.isSystem());
        verify(ruleRepository).save(existing);
    }

    /** The frontend sends this flag on every PUT; it used to be dropped silently. */
    @Test
    void updateRule_persistsTriggerForFinishedTeams() {
        AutomationRule existing = validDraft();
        existing.setGameId(gameId);
        existing.setTriggerForFinishedTeams(false);
        when(ruleRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        AutomationRule draft = validDraft();
        draft.setTriggerForFinishedTeams(true);

        assertTrue(service.updateRule(gameId, existing.getId(), draft).isTriggerForFinishedTeams());
    }

    @Test
    void updateRule_clearsTriggerForFinishedTeams() {
        AutomationRule existing = validDraft();
        existing.setGameId(gameId);
        existing.setTriggerForFinishedTeams(true);
        when(ruleRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        AutomationRule draft = validDraft();
        draft.setTriggerForFinishedTeams(false);

        assertFalse(service.updateRule(gameId, existing.getId(), draft).isTriggerForFinishedTeams());
    }

    @Test
    void updateRule_rejectsChangingNormalRuleIntoDistanceRule() {
        AutomationRule existing = validDraft();
        existing.setGameId(gameId);
        when(ruleRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        AutomationRule draft = new AutomationRule();
        draft.setName("Sneaky");
        draft.setTriggerType(AutomationTriggerType.DISTANCE_TRAVELED);
        draft.setTriggerConfig(new AutomationTriggerConfig(null, null, null, null, null, null, null, 1, null));

        assertThrows(BadRequestException.class,
            () -> service.updateRule(gameId, existing.getId(), draft));
    }

    @Test
    void deleteRule_rejectsSystemRule() {
        AutomationRule existing = existingSystemRule();

        assertThrows(BadRequestException.class, () -> service.deleteRule(gameId, existing.getId()));
        verify(ruleRepository, never()).delete(any());
    }

    @Test
    void deleteRule_removesNormalRule() {
        AutomationRule existing = validDraft();
        existing.setGameId(gameId);
        when(ruleRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        service.deleteRule(gameId, existing.getId());

        verify(ruleRepository).delete(existing.getId());
    }

    @Test
    void getRule_throwsNotFound_forRuleOfOtherGame() {
        AutomationRule foreign = validDraft();
        foreign.setGameId(UUID.randomUUID());
        when(ruleRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThrows(NotFoundException.class, () -> service.getRule(gameId, foreign.getId()));
    }

    @Test
    void setEnabled_rejectsSystemRule() {
        AutomationRule existing = existingSystemRule();

        // The distance rule follows the game status; it cannot be toggled by hand.
        assertThrows(BadRequestException.class,
            () -> service.setEnabled(gameId, existing.getId(), false));
        verify(ruleRepository, never()).save(any());
    }

    @Test
    void setEnabled_togglesNormalRule() {
        AutomationRule existing = validDraft();
        existing.setGameId(gameId);
        when(ruleRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        AutomationRule updated = service.setEnabled(gameId, existing.getId(), false);

        assertFalse(updated.isEnabled());
        verify(ruleRepository).save(existing);
    }

    @Test
    void updateRule_ignoresEnabledOnSystemRule() {
        AutomationRule existing = existingSystemRule();
        existing.setEnabled(true);
        AutomationRule draft = new AutomationRule();
        draft.setName("Afstandspunten");
        draft.setEnabled(false); // would disable a non-system rule
        draft.setTriggerType(AutomationTriggerType.DISTANCE_TRAVELED);
        draft.setTriggerConfig(new AutomationTriggerConfig(null, null, null, null, null, null, null, 3, null));
        draft.setFireOnce(false);

        AutomationRule updated = service.updateRule(gameId, existing.getId(), draft);

        assertTrue(updated.isEnabled(), "system rule enabled flag is lifecycle-managed, not from the draft");
        assertEquals(3, updated.getTriggerConfig().pointsPer100m());
    }

    // ==================== system distance rule creation ====================

    @Test
    void createSystemDistanceRule_isSystemRule_withDefaultRate() {
        AutomationRule rule = service.createSystemDistanceRule(gameId);

        assertTrue(rule.isSystem());
        assertFalse(rule.isFireOnce());
        assertEquals(AutomationTriggerType.DISTANCE_TRAVELED, rule.getTriggerType());
        assertEquals(AutomationService.DEFAULT_POINTS_PER_100M, rule.getTriggerConfig().pointsPer100m());
        assertEquals(AutomationService.SYSTEM_DISTANCE_RULE_NAME, rule.getName());
        verify(ruleRepository).save(rule);
    }

    // ==================== copyRules ====================

    private UUID sourceGameId;

    private Game game(Instant start, Instant end) {
        Game g = new Game();
        g.setStartTime(start);
        g.setEndTime(end);
        return g;
    }

    private void stubGames(Game source, Game target) {
        sourceGameId = UUID.randomUUID();
        lenient().when(gameService.findById(sourceGameId)).thenReturn(Optional.of(source));
        lenient().when(gameService.findById(gameId)).thenReturn(Optional.of(target));
    }

    private AutomationRule sourceRule(String name, AutomationTriggerType type, AutomationTriggerConfig config) {
        AutomationRule rule = new AutomationRule();
        rule.setGameId(sourceGameId);
        rule.setName(name);
        rule.setTriggerType(type);
        rule.setTriggerConfig(config);
        rule.setActions(List.of(new AutomationAction.SendMessage("Hoi {team}", null, null, null)));
        return rule;
    }

    private CircleCheckpoint checkpoint(UUID gameRef, String externalId, String name, Double lat, Double lng) {
        CircleCheckpoint cp = new CircleCheckpoint();
        cp.setGameId(gameRef);
        cp.setExternalId(externalId);
        cp.setName(name);
        cp.setLatitude(lat);
        cp.setLongitude(lng);
        return cp;
    }

    /** The non-system rule(s) that copyRules created via createRule. */
    private List<AutomationRule> capturedCreatedRules() {
        ArgumentCaptor<AutomationRule> captor = ArgumentCaptor.forClass(AutomationRule.class);
        verify(ruleRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues().stream()
            .filter(r -> gameId.equals(r.getGameId()) && !r.isSystem())
            .toList();
    }

    @Test
    void copyRules_copiesNonSystemRule_dropsTargetTeams() {
        stubGames(game(null, null), game(null, null));
        AutomationRule rule = sourceRule("Felicitatie", AutomationTriggerType.CHECKPOINT_ENTRY,
            new AutomationTriggerConfig(null, null, null, null, null, null, null, null, null));
        rule.setTargetTeamIds(List.of(UUID.randomUUID(), UUID.randomUUID()));
        when(ruleRepository.findByGameId(sourceGameId)).thenReturn(List.of(rule));

        CopyAutomationsResult result = service.copyRules(gameId, sourceGameId, adminId);

        assertEquals(1, result.copied());
        AutomationRule created = capturedCreatedRules().getFirst();
        assertEquals(gameId, created.getGameId());
        assertEquals("Felicitatie", created.getName());
        assertTrue(created.getTargetTeamIds().isEmpty(), "team ids are game-specific and must be dropped");
    }

    @Test
    void copyRules_remapsCheckpointByExternalId() {
        stubGames(game(null, null), game(null, null));
        UUID sourceCpId = UUID.randomUUID();
        AutomationRule rule = sourceRule("CP-regel", AutomationTriggerType.CHECKPOINT_ENTRY,
            new AutomationTriggerConfig(sourceCpId, null, null, null, null, null, null, null, null));
        when(ruleRepository.findByGameId(sourceGameId)).thenReturn(List.of(rule));

        Checkpoint sourceCp = checkpoint(sourceGameId, "CP5", "Finish", 52.0, 4.0);
        when(checkpointRepository.findById(sourceCpId)).thenReturn(Optional.of(sourceCp));
        CircleCheckpoint targetCp = checkpoint(gameId, "CP5", "Finish (target)", 99.0, 99.0);
        targetCp.setId(UUID.randomUUID());
        when(checkpointRepository.findByGameId(gameId)).thenReturn(List.of(targetCp));
        when(checkpointRepository.findById(targetCp.getId())).thenReturn(Optional.of(targetCp));

        CopyAutomationsResult result = service.copyRules(gameId, sourceGameId, adminId);

        assertEquals(1, result.copied());
        assertEquals(targetCp.getId(), capturedCreatedRules().getFirst().getTriggerConfig().checkpointId());
    }

    @Test
    void copyRules_remapsCheckpointIdSet() {
        stubGames(game(null, null), game(null, null));
        UUID sourceCpId = UUID.randomUUID();
        AutomationRule rule = sourceRule("CP-set-regel", AutomationTriggerType.CHECKPOINT_ENTRY,
            new AutomationTriggerConfig(null, null, null, null, null, null, null, null, null,
                null, null, List.of(sourceCpId)));
        when(ruleRepository.findByGameId(sourceGameId)).thenReturn(List.of(rule));

        Checkpoint sourceCp = checkpoint(sourceGameId, "CP5", "Finish", 52.0, 4.0);
        when(checkpointRepository.findById(sourceCpId)).thenReturn(Optional.of(sourceCp));
        CircleCheckpoint targetCp = checkpoint(gameId, "CP5", "Finish (target)", 99.0, 99.0);
        targetCp.setId(UUID.randomUUID());
        when(checkpointRepository.findByGameId(gameId)).thenReturn(List.of(targetCp));
        when(checkpointRepository.findById(targetCp.getId())).thenReturn(Optional.of(targetCp));

        CopyAutomationsResult result = service.copyRules(gameId, sourceGameId, adminId);

        assertEquals(1, result.copied());
        assertEquals(List.of(targetCp.getId()),
            capturedCreatedRules().getFirst().getTriggerConfig().checkpointIds());
    }

    @Test
    void copyRules_remapsCheckpointByLocation_whenNoExternalId() {
        stubGames(game(null, null), game(null, null));
        UUID sourceCpId = UUID.randomUUID();
        AutomationRule rule = sourceRule("CP-regel", AutomationTriggerType.CHECKPOINT_ENTRY,
            new AutomationTriggerConfig(sourceCpId, null, null, null, null, null, null, null, null));
        when(ruleRepository.findByGameId(sourceGameId)).thenReturn(List.of(rule));

        Checkpoint sourceCp = checkpoint(sourceGameId, null, "Finish", 52.370216, 4.895168);
        when(checkpointRepository.findById(sourceCpId)).thenReturn(Optional.of(sourceCp));
        CircleCheckpoint nearTarget = checkpoint(gameId, null, "Eindpunt", 52.370220, 4.895170); // ~1 m away
        nearTarget.setId(UUID.randomUUID());
        when(checkpointRepository.findByGameId(gameId)).thenReturn(List.of(nearTarget));
        when(checkpointRepository.findById(nearTarget.getId())).thenReturn(Optional.of(nearTarget));

        CopyAutomationsResult result = service.copyRules(gameId, sourceGameId, adminId);

        assertEquals(1, result.copied());
        assertEquals(nearTarget.getId(), capturedCreatedRules().getFirst().getTriggerConfig().checkpointId());
    }

    @Test
    void copyRules_skipsUnmatchedCheckpointRule() {
        stubGames(game(null, null), game(null, null));
        UUID sourceCpId = UUID.randomUUID();
        AutomationRule rule = sourceRule("Finishbonus", AutomationTriggerType.CHECKPOINT_ENTRY,
            new AutomationTriggerConfig(sourceCpId, null, null, null, null, null, null, null, null));
        when(ruleRepository.findByGameId(sourceGameId)).thenReturn(List.of(rule));

        Checkpoint sourceCp = checkpoint(sourceGameId, "CP5", "Finish", 52.0, 4.0);
        when(checkpointRepository.findById(sourceCpId)).thenReturn(Optional.of(sourceCp));
        // Target has a checkpoint, but different external id and far away.
        CircleCheckpoint other = checkpoint(gameId, "CP9", "Start", 10.0, 10.0);
        other.setId(UUID.randomUUID());
        when(checkpointRepository.findByGameId(gameId)).thenReturn(List.of(other));

        CopyAutomationsResult result = service.copyRules(gameId, sourceGameId, adminId);

        assertEquals(0, result.copied());
        assertEquals(List.of("Finishbonus"), result.skippedUnmatchedCheckpoint());
    }

    @Test
    void copyRules_skipsDuplicateNameCaseInsensitive() {
        stubGames(game(null, null), game(null, null));
        AutomationRule rule = sourceRule("Felicitatie", AutomationTriggerType.RIVAL_OVERTAKE,
            new AutomationTriggerConfig(null, null, null, null, null, null, null, null, null));
        when(ruleRepository.findByGameId(sourceGameId)).thenReturn(List.of(rule));

        AutomationRule existing = new AutomationRule();
        existing.setGameId(gameId);
        existing.setName("felicitatie");
        when(ruleRepository.findByGameId(gameId)).thenReturn(List.of(existing));

        CopyAutomationsResult result = service.copyRules(gameId, sourceGameId, adminId);

        assertEquals(0, result.copied());
        assertEquals(List.of("Felicitatie"), result.skippedDuplicate());
    }

    @Test
    void copyRules_copiesDistanceRateOnly_notEnabled() {
        stubGames(game(null, null), game(null, null));
        AutomationRule srcDist = new AutomationRule();
        srcDist.setGameId(sourceGameId);
        srcDist.setName("Afstandspunten");
        srcDist.setSystem(true);
        srcDist.setEnabled(false);
        srcDist.setTriggerType(AutomationTriggerType.DISTANCE_TRAVELED);
        srcDist.setTriggerConfig(new AutomationTriggerConfig(null, null, null, null, null, null, null, 5, null));

        AutomationRule tgtDist = new AutomationRule();
        tgtDist.setGameId(gameId);
        tgtDist.setName("Afstandspunten");
        tgtDist.setSystem(true);
        tgtDist.setEnabled(true);
        tgtDist.setTriggerType(AutomationTriggerType.DISTANCE_TRAVELED);
        tgtDist.setTriggerConfig(new AutomationTriggerConfig(null, null, null, null, null, null, null, 1, null));

        when(ruleRepository.findByGameId(sourceGameId)).thenReturn(List.of(srcDist));
        when(ruleRepository.findByGameIdAndTriggerType(sourceGameId, AutomationTriggerType.DISTANCE_TRAVELED))
            .thenReturn(List.of(srcDist));
        when(ruleRepository.findByGameIdAndTriggerType(gameId, AutomationTriggerType.DISTANCE_TRAVELED))
            .thenReturn(List.of(tgtDist));

        CopyAutomationsResult result = service.copyRules(gameId, sourceGameId, adminId);

        assertTrue(result.distanceRateCopied());
        assertEquals(5, tgtDist.getTriggerConfig().pointsPer100m());
        // enabled stays driven by the target game's own status, not the source's.
        assertTrue(tgtDist.isEnabled());
        verify(ruleRepository).save(tgtDist);
    }

    @Test
    void copyRules_shiftsAbsoluteTimeByNearestAnchor() {
        Instant srcStart = Instant.parse("2026-06-13T08:00:00Z");
        Instant srcEnd = Instant.parse("2026-06-13T17:00:00Z");
        Instant tgtStart = Instant.parse("2026-07-04T08:30:00Z");  // +30 min
        Instant tgtEnd = Instant.parse("2026-07-04T18:00:00Z");    // +1 h (and +21 days)
        stubGames(game(srcStart, srcEnd), game(tgtStart, tgtEnd));

        // 16:30 sits closest to the end anchor → shift by the end delta.
        Instant deadline = Instant.parse("2026-06-13T16:30:00Z");
        AutomationRule rule = sourceRule("Finish op tijd", AutomationTriggerType.CHECKPOINT_ENTRY,
            new AutomationTriggerConfig(null, AutomationTriggerConfig.ScheduleMode.ABSOLUTE, deadline,
                null, null, null, null, null, AutomationTriggerConfig.PassComparison.BEFORE));
        when(ruleRepository.findByGameId(sourceGameId)).thenReturn(List.of(rule));

        service.copyRules(gameId, sourceGameId, adminId);

        Instant shifted = capturedCreatedRules().getFirst().getTriggerConfig().absoluteTime();
        Instant expected = deadline.plus(Duration.between(srcEnd, tgtEnd));
        assertEquals(expected, shifted);
    }

    @Test
    void copyRules_leavesRelativeScheduleUnchanged() {
        stubGames(game(null, null), game(null, null));
        AutomationRule rule = sourceRule("Bijna klaar", AutomationTriggerType.SCHEDULED_TIME,
            new AutomationTriggerConfig(null, AutomationTriggerConfig.ScheduleMode.RELATIVE, null,
                AutomationTriggerConfig.RelativeAnchor.GAME_END, -30, null, null, null, null));
        when(ruleRepository.findByGameId(sourceGameId)).thenReturn(List.of(rule));

        service.copyRules(gameId, sourceGameId, adminId);

        AutomationTriggerConfig config = capturedCreatedRules().getFirst().getTriggerConfig();
        assertEquals(AutomationTriggerConfig.RelativeAnchor.GAME_END, config.relativeTo());
        assertEquals(-30, config.offsetMinutes());
        assertNull(config.absoluteTime());
    }

    @Test
    void copyRules_rejectsSameGame() {
        assertThrows(BadRequestException.class, () -> service.copyRules(gameId, gameId, adminId));
    }
}
