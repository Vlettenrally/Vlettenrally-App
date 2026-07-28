package nl.vlettenrally.service.impl;

import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import nl.vlettenrally.data.domain.Achievement;
import nl.vlettenrally.data.domain.Checkpoint;
import nl.vlettenrally.data.domain.CircleCheckpoint;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.enums.AchievementStatus;
import nl.vlettenrally.data.enums.PointEntryType;
import nl.vlettenrally.data.repository.AchievementRepository;
import nl.vlettenrally.data.repository.CheckpointRepository;
import nl.vlettenrally.data.repository.FcmTokenRepository;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.fcm.FcmHttpClient;
import nl.vlettenrally.service.AutomationEngine;
import nl.vlettenrally.service.PointsLedgerService;
import nl.vlettenrally.service.PointsTotalsCache;
import nl.vlettenrally.websockets.AdminTopicSubscriptionManager;
import nl.vlettenrally.websockets.CheckpointFcmSubscriptionManager;
import nl.vlettenrally.websockets.WebSocketPublisher;
import nl.vlettenrally.websockets.dto.WebSocketMessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the auto-verified checkpoint pass flow and the bonus-points
 * adjustment used by checkpoint personnel after auto-verification.
 */
@ExtendWith(MockitoExtension.class)
class AchievementServiceImplTest {

    @Mock AchievementRepository achievementRepository;
    @Mock CheckpointRepository checkpointRepository;
    @Mock TeamRepository teamRepository;
    @Mock WebSocketPublisher webSocketPublisher;
    @Mock AdminTopicSubscriptionManager adminTopicSubscriptionManager;
    @Mock CheckpointFcmSubscriptionManager checkpointFcmSubscriptionManager;
    @Mock FcmTokenRepository fcmTokenRepository;
    @Mock FcmHttpClient fcmHttpClient;
    @Mock PointsLedgerService pointsLedgerService;
    @Mock PointsTotalsCache pointsTotalsCache;
    @Mock TransactionSynchronizationRegistry txRegistry;
    @Mock AutomationEngine automationEngine;

    @InjectMocks AchievementServiceImpl service;

    private UUID teamId;
    private UUID checkpointId;
    private UUID gameId;
    private Team team;
    private Checkpoint checkpoint;

    @BeforeEach
    void setUp() {
        teamId = UUID.randomUUID();
        checkpointId = UUID.randomUUID();
        gameId = UUID.randomUUID();

        team = new Team();
        team.setId(teamId);
        team.setGameId(gameId);
        team.setName("300");

        CircleCheckpoint cp = new CircleCheckpoint();
        cp.setId(checkpointId);
        cp.setGameId(gameId);
        cp.setName("Start");
        cp.setPoints(10);
        cp.setLatitude(35.139407);
        cp.setLongitude(-89.835925);
        checkpoint = cp;

        lenient().when(pointsTotalsCache.get(teamId)).thenReturn(100);
    }

    /** Run every post-commit synchronization the call registered, as a real commit would. */
    private void fireAfterCommit() {
        ArgumentCaptor<Synchronization> captor = ArgumentCaptor.forClass(Synchronization.class);
        verify(txRegistry, atLeastOnce()).registerInterposedSynchronization(captor.capture());
        for (Synchronization sync : captor.getAllValues()) {
            sync.afterCompletion(Status.STATUS_COMMITTED);
        }
    }

    @Test
    void recordVerifiedPass_createsVerifiedAchievement_whenNoneExists() {
        Instant passTime = Instant.parse("2026-03-09T17:45:34Z");
        when(achievementRepository.findByTeamIdAndCheckpointId(teamId, checkpointId))
            .thenReturn(Optional.empty());
        when(checkpointRepository.findById(checkpointId)).thenReturn(Optional.of(checkpoint));
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(achievementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<Achievement> result = service.recordVerifiedPass(teamId, checkpointId, passTime);

        assertTrue(result.isPresent());
        Achievement saved = result.get();
        assertEquals(AchievementStatus.VERIFIED, saved.getStatus());
        assertEquals(passTime, saved.getDetectedAt());
        assertEquals(passTime, saved.getVerifiedAt());
        assertEquals(0, saved.getBonusPoints());

        verify(pointsLedgerService).createEntry(
            eq(teamId), eq(gameId), eq(PointEntryType.CHECKPOINT_VERIFICATION),
            eq(10), any(), any(), any(), eq(null));
        verify(automationEngine).onCheckpointPass(eq(gameId), eq(teamId), eq(checkpointId), eq(passTime));
    }

    @Test
    void recordVerifiedPass_promotesPendingToVerified_preservingBonusPoints() {
        Instant passTime = Instant.parse("2026-03-09T17:45:34Z");
        Achievement pending = new Achievement();
        pending.setTeamId(teamId);
        pending.setCheckpointId(checkpointId);
        pending.setStatus(AchievementStatus.PENDING);
        pending.setDetectedAt(passTime.minusSeconds(30));
        pending.setBonusPoints(5); // operator already added bonus during PENDING

        when(achievementRepository.findByTeamIdAndCheckpointId(teamId, checkpointId))
            .thenReturn(Optional.of(pending));
        when(checkpointRepository.findById(checkpointId)).thenReturn(Optional.of(checkpoint));
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(achievementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<Achievement> result = service.recordVerifiedPass(teamId, checkpointId, passTime);

        assertTrue(result.isPresent());
        Achievement saved = result.get();
        assertEquals(AchievementStatus.VERIFIED, saved.getStatus());
        assertEquals(passTime, saved.getVerifiedAt());
        assertEquals(5, saved.getBonusPoints(), "operator-set bonus must be preserved");

        verify(pointsLedgerService).createEntry(
            eq(teamId), eq(gameId), eq(PointEntryType.CHECKPOINT_VERIFICATION),
            eq(15), any(), any(), any(), eq(null)); // base 10 + bonus 5
    }

    @Test
    void recordVerifiedPass_isNoOp_whenAlreadyVerified() {
        Instant earlier = Instant.parse("2026-03-09T17:45:34Z");
        Instant later = Instant.parse("2026-03-09T18:00:00Z");
        Achievement existing = new Achievement();
        existing.setTeamId(teamId);
        existing.setCheckpointId(checkpointId);
        existing.setStatus(AchievementStatus.VERIFIED);
        existing.setDetectedAt(earlier);
        existing.setVerifiedAt(earlier);
        existing.setBonusPoints(3);

        when(checkpointRepository.findById(checkpointId)).thenReturn(Optional.of(checkpoint));
        when(achievementRepository.findByTeamIdAndCheckpointId(teamId, checkpointId))
            .thenReturn(Optional.of(existing));

        Optional<Achievement> result = service.recordVerifiedPass(teamId, checkpointId, later);

        assertTrue(result.isEmpty(), "VERIFIED record must not be modified by repeat polls");
        verify(achievementRepository, never()).save(any());
        verify(pointsLedgerService, never()).createEntry(any(), any(), any(), anyInt(), any(), any(), any(), any());
        verify(automationEngine, never()).onCheckpointPass(any(), any(), any(), any());
    }

    @Test
    void recordVerifiedPass_isNoOp_whenRejected() {
        Achievement rejected = new Achievement();
        rejected.setTeamId(teamId);
        rejected.setCheckpointId(checkpointId);
        rejected.setStatus(AchievementStatus.REJECTED);

        when(checkpointRepository.findById(checkpointId)).thenReturn(Optional.of(checkpoint));
        when(achievementRepository.findByTeamIdAndCheckpointId(teamId, checkpointId))
            .thenReturn(Optional.of(rejected));

        Optional<Achievement> result = service.recordVerifiedPass(teamId, checkpointId, Instant.now());

        assertTrue(result.isEmpty());
        verify(achievementRepository, never()).save(any());
        verify(pointsLedgerService, never()).createEntry(any(), any(), any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    void recordVerifiedPass_skipsPass_whenCheckpointClosed() {
        // Pass recorded after the checkpoint's closing time: automatic awarding is
        // suppressed entirely — no achievement, no points, no automation.
        checkpoint.setOpeningTime(Instant.parse("2026-03-09T10:00:00Z"));
        checkpoint.setClosingTime(Instant.parse("2026-03-09T14:00:00Z"));
        Instant passTime = Instant.parse("2026-03-09T15:30:00Z");

        when(checkpointRepository.findById(checkpointId)).thenReturn(Optional.of(checkpoint));

        Optional<Achievement> result = service.recordVerifiedPass(teamId, checkpointId, passTime);

        assertTrue(result.isEmpty(), "out-of-window automatic pass must not be recorded");
        verify(achievementRepository, never()).save(any());
        verify(pointsLedgerService, never()).createEntry(any(), any(), any(), anyInt(), any(), any(), any(), any());
        verify(automationEngine, never()).onCheckpointPass(any(), any(), any(), any());
    }

    @Test
    void recordVerifiedPass_awardsPass_whenWithinWindow() {
        checkpoint.setOpeningTime(Instant.parse("2026-03-09T10:00:00Z"));
        checkpoint.setClosingTime(Instant.parse("2026-03-09T14:00:00Z"));
        Instant passTime = Instant.parse("2026-03-09T12:00:00Z");

        when(checkpointRepository.findById(checkpointId)).thenReturn(Optional.of(checkpoint));
        when(achievementRepository.findByTeamIdAndCheckpointId(teamId, checkpointId))
            .thenReturn(Optional.empty());
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(achievementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<Achievement> result = service.recordVerifiedPass(teamId, checkpointId, passTime);

        assertTrue(result.isPresent());
        verify(pointsLedgerService).createEntry(
            eq(teamId), eq(gameId), eq(PointEntryType.CHECKPOINT_VERIFICATION),
            eq(10), any(), any(), any(), eq(null));
        verify(automationEngine).onCheckpointPass(eq(gameId), eq(teamId), eq(checkpointId), eq(passTime));
    }

    @Test
    void addBonusPoints_addsDeltaAndLedgersIt_onVerifiedAchievement() {
        UUID achievementId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        Achievement verified = new Achievement();
        verified.setId(achievementId);
        verified.setTeamId(teamId);
        verified.setCheckpointId(checkpointId);
        verified.setStatus(AchievementStatus.VERIFIED);
        verified.setBonusPoints(2);

        when(achievementRepository.findById(achievementId)).thenReturn(Optional.of(verified));
        when(checkpointRepository.findById(checkpointId)).thenReturn(Optional.of(checkpoint));
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(achievementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<Achievement> result = service.addBonusPoints(achievementId, 3, operatorId);

        assertTrue(result.isPresent());
        assertEquals(5, result.get().getBonusPoints());

        ArgumentCaptor<Integer> amountCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(pointsLedgerService).createEntry(
            eq(teamId), eq(gameId), eq(PointEntryType.MANUAL_ADJUSTMENT),
            amountCaptor.capture(), any(), any(), eq(achievementId), eq(operatorId));
        assertEquals(3, amountCaptor.getValue(), "ledger receives only the delta, not the new total");
    }

    @Test
    void addBonusPoints_isRejected_onNonVerifiedAchievement() {
        UUID achievementId = UUID.randomUUID();
        Achievement pending = new Achievement();
        pending.setId(achievementId);
        pending.setStatus(AchievementStatus.PENDING);

        when(achievementRepository.findById(achievementId)).thenReturn(Optional.of(pending));

        Optional<Achievement> result = service.addBonusPoints(achievementId, 5, null);

        assertTrue(result.isEmpty(), "bonus edits should only apply to VERIFIED records");
        verify(achievementRepository, never()).save(any());
        verify(pointsLedgerService, never()).createEntry(any(), any(), any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    void addBonusPoints_zeroDelta_isNoOp() {
        UUID achievementId = UUID.randomUUID();
        Achievement verified = new Achievement();
        verified.setId(achievementId);
        verified.setStatus(AchievementStatus.VERIFIED);
        verified.setBonusPoints(7);
        when(achievementRepository.findById(achievementId)).thenReturn(Optional.of(verified));

        Optional<Achievement> result = service.addBonusPoints(achievementId, 0, null);

        assertTrue(result.isPresent());
        assertEquals(7, result.get().getBonusPoints());
        verify(achievementRepository, never()).save(any());
        verify(pointsLedgerService, never()).createEntry(any(), any(), any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    void recordVerifiedPass_writesSystemMarker_verifiedByIsNull() {
        Instant passTime = Instant.parse("2026-03-09T17:45:34Z");
        when(achievementRepository.findByTeamIdAndCheckpointId(teamId, checkpointId))
            .thenReturn(Optional.empty());
        when(checkpointRepository.findById(checkpointId)).thenReturn(Optional.of(checkpoint));
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(achievementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<Achievement> result = service.recordVerifiedPass(teamId, checkpointId, passTime);

        assertTrue(result.isPresent());
        // verifiedBy left null distinguishes system-verified from operator-verified records.
        org.junit.jupiter.api.Assertions.assertNull(result.get().getVerifiedBy());
    }

    @SuppressWarnings("unchecked")
    @Test
    void recordVerifiedPass_createsVerifiedAchievement_pushesTeamAchievementAfterCommit() {
        Instant passTime = Instant.parse("2026-03-09T17:45:34Z");
        when(achievementRepository.findByTeamIdAndCheckpointId(teamId, checkpointId))
            .thenReturn(Optional.empty());
        when(checkpointRepository.findById(checkpointId)).thenReturn(Optional.of(checkpoint));
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(achievementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<Achievement> result = service.recordVerifiedPass(teamId, checkpointId, passTime);
        assertTrue(result.isPresent());

        verify(webSocketPublisher, never()).sendToTeam(any(), any(), any(), any());
        fireAfterCommit();

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(webSocketPublisher).sendToTeam(
            eq(gameId), eq(teamId), eq(WebSocketMessageType.TEAM_ACHIEVEMENT.getValue()), payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertEquals(result.get().getId(), payload.get("id"));
        assertEquals(checkpointId, payload.get("checkpointId"));
        assertEquals(10, payload.get("pointsEarned"));
        assertEquals(110, payload.get("totalPoints"), "baseline 100 (mocked cache) + 10 earned");
    }

    @SuppressWarnings("unchecked")
    @Test
    void recordVerifiedPass_promotesPendingToVerified_pushesTeamAchievementAfterCommit() {
        Instant passTime = Instant.parse("2026-03-09T17:45:34Z");
        Achievement pending = new Achievement();
        pending.setTeamId(teamId);
        pending.setCheckpointId(checkpointId);
        pending.setStatus(AchievementStatus.PENDING);
        pending.setDetectedAt(passTime.minusSeconds(30));
        pending.setBonusPoints(5);

        when(achievementRepository.findByTeamIdAndCheckpointId(teamId, checkpointId))
            .thenReturn(Optional.of(pending));
        when(checkpointRepository.findById(checkpointId)).thenReturn(Optional.of(checkpoint));
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(achievementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.recordVerifiedPass(teamId, checkpointId, passTime);
        fireAfterCommit();

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(webSocketPublisher).sendToTeam(
            eq(gameId), eq(teamId), eq(WebSocketMessageType.TEAM_ACHIEVEMENT.getValue()), payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertEquals(15, payload.get("pointsEarned"), "base 10 + preserved bonus 5");
        assertEquals(115, payload.get("totalPoints"), "baseline 100 (mocked cache) + 15 earned");
    }

    @SuppressWarnings("unchecked")
    @Test
    void verifyAchievement_pushesTeamAchievementAfterCommit() {
        UUID achievementId = UUID.randomUUID();
        UUID gameMasterId = UUID.randomUUID();
        Achievement pending = new Achievement();
        pending.setId(achievementId);
        pending.setTeamId(teamId);
        pending.setCheckpointId(checkpointId);
        pending.setStatus(AchievementStatus.PENDING);

        when(achievementRepository.findById(achievementId)).thenReturn(Optional.of(pending));
        when(checkpointRepository.findById(checkpointId)).thenReturn(Optional.of(checkpoint));
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(achievementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<Achievement> result = service.verifyAchievement(achievementId, gameMasterId, "notes", 5);
        assertTrue(result.isPresent());
        fireAfterCommit();

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(webSocketPublisher).sendToTeam(
            eq(gameId), eq(teamId), eq(WebSocketMessageType.TEAM_ACHIEVEMENT.getValue()), payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertEquals(achievementId, payload.get("id"));
        assertEquals(15, payload.get("pointsEarned"), "base 10 + bonus 5");
        assertEquals(115, payload.get("totalPoints"), "baseline 100 (mocked cache) + 15 earned");
    }
}
