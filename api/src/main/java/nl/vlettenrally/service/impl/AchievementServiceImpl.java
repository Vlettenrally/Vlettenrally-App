package nl.vlettenrally.service.impl;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import nl.vlettenrally.data.domain.Achievement;
import nl.vlettenrally.data.domain.Checkpoint;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.dto.achievement.CheckpointTeamResponse;
import nl.vlettenrally.data.enums.AchievementStatus;
import nl.vlettenrally.data.enums.PointEntryType;
import nl.vlettenrally.data.repository.AchievementRepository;
import nl.vlettenrally.data.repository.CheckpointRepository;
import nl.vlettenrally.data.repository.FcmTokenRepository;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.fcm.FcmHttpClient;
import nl.vlettenrally.service.AchievementService;
import nl.vlettenrally.service.AutomationEngine;
import nl.vlettenrally.service.PointsLedgerService;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import nl.vlettenrally.websockets.CheckpointFcmSubscriptionManager;
import nl.vlettenrally.websockets.AdminTopicSubscriptionManager;
import nl.vlettenrally.websockets.AdminTopics;
import nl.vlettenrally.websockets.WebSocketMessage;
import nl.vlettenrally.websockets.WebSocketPublisher;
import nl.vlettenrally.websockets.dto.CheckpointDetectionPayload;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Implementation of AchievementService.
 * <p>
 * Handles the achievement verification workflow:
 * 1. detectProximity() - System creates PENDING achievement when team is near checkpoint
 * 2. verifyAchievement() - Game master confirms, points awarded
 * 3. rejectAchievement() - Game master rejects, no points
 */
@ApplicationScoped
public class AchievementServiceImpl implements AchievementService {

    @Inject
    AchievementRepository achievementRepository;

    @Inject
    CheckpointRepository checkpointRepository;

    @Inject
    TeamRepository teamRepository;

    @Inject
    WebSocketPublisher webSocketPublisher;

    @Inject
    AdminTopicSubscriptionManager adminTopicSubscriptionManager;

    @Inject
    CheckpointFcmSubscriptionManager checkpointFcmSubscriptionManager;

    @Inject
    FcmTokenRepository fcmTokenRepository;

    @Inject
    FcmHttpClient fcmHttpClient;

    @Inject
    PointsLedgerService pointsLedgerService;

    @Inject
    AutomationEngine automationEngine;

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @ConfigProperty(name = "vlettenrally.achievement.rejection-expiry-minutes", defaultValue = "15")
    long rejectionExpiryMinutes;

    /**
     * Drops REJECTED achievements whose verifiedAt is older than the configured
     * expiry, so the team becomes detectable again at the same checkpoint.
     */
    private void purgeExpiredRejections() {
        Instant cutoff = Instant.now().minusSeconds(rejectionExpiryMinutes * 60);
        int removed = achievementRepository.deleteRejectedOlderThan(cutoff);
        if (removed > 0) {
            Log.debugf("Expired %d rejected achievement(s) older than %d min", removed, rejectionExpiryMinutes);
        }
    }

    @Override
    public Optional<Achievement> findById(UUID id) {
        return achievementRepository.findById(id);
    }

    @Override
    public List<Achievement> findAll() {
        return achievementRepository.findAll();
    }

    @Override
    public List<Achievement> findByTeamId(UUID teamId) {
        return achievementRepository.findByTeamId(teamId);
    }

    @Override
    public List<Achievement> findByCheckpointId(UUID checkpointId) {
        return achievementRepository.findByCheckpointId(checkpointId);
    }

    @Override
    public Optional<Achievement> findByTeamIdAndCheckpointId(UUID teamId, UUID checkpointId) {
        return achievementRepository.findByTeamIdAndCheckpointId(teamId, checkpointId);
    }

    @Override
    public List<Achievement> findPendingByCheckpointId(UUID checkpointId) {
        return achievementRepository.findByCheckpointId(checkpointId).stream()
            .filter(a -> a.getStatus() == AchievementStatus.PENDING)
            .toList();
    }

    @Override
    public List<Achievement> findAllPending() {
        return achievementRepository.findAll().stream()
            .filter(a -> a.getStatus() == AchievementStatus.PENDING)
            .toList();
    }

    @Override
    public Optional<Achievement> detectProximity(UUID teamId, UUID checkpointId) {
        // Drop stale rejections so a team can be re-detected after the expiry window
        purgeExpiredRejections();

        // Check if already detected or verified for this checkpoint
        Optional<Achievement> existing = achievementRepository.findByTeamIdAndCheckpointId(teamId, checkpointId);
        if (existing.isPresent()) {
            return Optional.empty(); // Already has an achievement record (pending, verified, or rejected)
        }

        // Get checkpoint for points value
        Checkpoint checkpoint = checkpointRepository.findById(checkpointId)
            .orElseThrow(() -> new IllegalArgumentException("Checkpoint not found: " + checkpointId));

        // Verify team exists
        teamRepository.findById(teamId)
            .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));

        // Create PENDING achievement (points are derived from the checkpoint at lookup time)
        Achievement achievement = new Achievement();
        achievement.setTeamId(teamId);
        achievement.setCheckpointId(checkpointId);
        achievement.setStatus(AchievementStatus.PENDING);
        achievement.setDetectedAt(Instant.now());

        Achievement saved = achievementRepository.save(achievement);

        // Notify checkpoint operators via WebSocket and FCM push
        Team team = teamRepository.findById(teamId).orElse(null);
        String teamName = team != null ? team.getName() : teamId.toString();

        CheckpointDetectionPayload wsPayload = new CheckpointDetectionPayload(
            saved.getId(),
            teamId,
            teamName,
            checkpointId,
            checkpoint.getName(),
            saved.getDetectedAt().toString()
        );

        notifyCheckpointOperators(checkpoint, teamId, teamName, wsPayload);

        return Optional.of(saved);
    }

    /**
     * Send CHECKPOINT_DETECTION events to admins manning this checkpoint.
     * <p>
     * WebSocket pushes go to sessions that are <em>actively viewing</em> the checkpoint
     * (heartbeat-based subscription, ~30 s timeout). FCM pushes go to admins who
     * have <em>explicitly subscribed</em> via the page lifecycle — they keep firing
     * even when the WebSocket drops temporarily, until the operator switches
     * checkpoint or leaves the page.
     */
    private void notifyCheckpointOperators(Checkpoint checkpoint, UUID teamId, String teamName, CheckpointDetectionPayload wsPayload) {
        // 1) Live WS push to actively-viewing sessions
        Set<String> subscribedSessions = adminTopicSubscriptionManager.getActiveSubscribers(checkpoint.getId(), AdminTopics.CHECKPOINT);
        for (String sessionId : subscribedSessions) {
            WebSocketMessage wsMessage = new WebSocketMessage("CHECKPOINT_DETECTION", wsPayload)
                .withTargetSession(sessionId);
            webSocketPublisher.publish(wsMessage);
        }

        // 2) Durable FCM push to subscribed admins (independent of WS state)
        Set<UUID> adminIds = checkpointFcmSubscriptionManager.getSubscribedAdmins(checkpoint.getId());
        if (adminIds.isEmpty()) {
            Log.debugf("No FCM subscribers for checkpoint %s — skipping push", checkpoint.getId());
            return;
        }

        fcmTokenRepository.findAdminTokensByAdminIds(adminIds).forEach(fcmToken -> {
            try {
                fcmHttpClient.sendNotification(
                    fcmToken.getToken(),
                    "Team gedetecteerd",
                    teamName + " is bij " + checkpoint.getName(),
                    Map.of(
                        "type", "CHECKPOINT_DETECTION",
                        "checkpointId", checkpoint.getId().toString(),
                        "teamId", teamId.toString()
                    )
                );
            } catch (FcmHttpClient.FcmException e) {
                Log.warnf("Failed to send FCM checkpoint detection to token %s: %s", fcmToken.getId(), e.getMessage());
            }
        });
    }

    @Override
    public Optional<Achievement> verifyAchievement(UUID achievementId, UUID gameMasterId, String notes, int bonusPoints) {
        Optional<Achievement> optAchievement = achievementRepository.findById(achievementId);
        if (optAchievement.isEmpty()) {
            return Optional.empty();
        }

        Achievement achievement = optAchievement.get();

        // Can only verify PENDING achievements
        if (achievement.getStatus() != AchievementStatus.PENDING) {
            return Optional.empty();
        }

        // Update achievement status
        achievement.setStatus(AchievementStatus.VERIFIED);
        achievement.setVerifiedAt(Instant.now());
        achievement.setVerifiedBy(gameMasterId);
        achievement.setNotes(notes);
        achievement.setBonusPoints(bonusPoints);

        // Award (current checkpoint base + bonus) to the team's total via the ledger
        Checkpoint checkpoint = checkpointRepository.findById(achievement.getCheckpointId())
            .orElseThrow(() -> new IllegalStateException("Checkpoint not found: " + achievement.getCheckpointId()));
        int total = checkpoint.getPoints() + bonusPoints;

        // Save the achievement before the ledger entry so totals stay consistent on a rollback
        Achievement savedAchievement = achievementRepository.save(achievement);

        Team team = teamRepository.findById(achievement.getTeamId())
            .orElseThrow(() -> new IllegalStateException("Team not found: " + achievement.getTeamId()));

        pointsLedgerService.createEntry(
            team.getId(),
            team.getGameId(),
            PointEntryType.CHECKPOINT_VERIFICATION,
            total,
            resolveCheckpointLocation(checkpoint),
            "Checkpoint " + checkpoint.getName() + " geverifieerd",
            savedAchievement.getId(),
            null
        );

        // Manual verification: detectedAt is when the team was actually in the
        // zone; verifiedAt is merely when the game master confirmed it.
        automationEngine.onCheckpointPass(team.getGameId(), team.getId(), checkpoint.getId(),
            savedAchievement.getDetectedAt() != null
                ? savedAchievement.getDetectedAt() : savedAchievement.getVerifiedAt());

        return Optional.of(savedAchievement);
    }

    /**
     * Prefer the PostGIS Point on the checkpoint; fall back to lat/lng when not set
     * (e.g. mock checkpoints don't populate the Point).
     */
    private Point resolveCheckpointLocation(Checkpoint checkpoint) {
        if (checkpoint.getLocation() != null) {
            return checkpoint.getLocation();
        }
        if (checkpoint.getLongitude() != null && checkpoint.getLatitude() != null) {
            return GEOMETRY_FACTORY.createPoint(
                new Coordinate(checkpoint.getLongitude(), checkpoint.getLatitude()));
        }
        return null;
    }

    @Override
    public Optional<Achievement> addBonusPoints(UUID achievementId, int bonusDelta, UUID gameMasterId) {
        if (bonusDelta == 0) {
            return achievementRepository.findById(achievementId);
        }
        Optional<Achievement> optAchievement = achievementRepository.findById(achievementId);
        if (optAchievement.isEmpty()) {
            return Optional.empty();
        }
        Achievement achievement = optAchievement.get();
        if (achievement.getStatus() != AchievementStatus.VERIFIED) {
            return Optional.empty();
        }

        achievement.setBonusPoints(achievement.getBonusPoints() + bonusDelta);
        Achievement saved = achievementRepository.save(achievement);

        Checkpoint checkpoint = checkpointRepository.findById(achievement.getCheckpointId())
            .orElseThrow(() -> new IllegalStateException("Checkpoint not found: " + achievement.getCheckpointId()));
        Team team = teamRepository.findById(achievement.getTeamId())
            .orElseThrow(() -> new IllegalStateException("Team not found: " + achievement.getTeamId()));

        pointsLedgerService.createEntry(
            team.getId(),
            team.getGameId(),
            PointEntryType.MANUAL_ADJUSTMENT,
            bonusDelta,
            resolveCheckpointLocation(checkpoint),
            "Bonuspunten " + checkpoint.getName(),
            saved.getId(),
            gameMasterId
        );
        return Optional.of(saved);
    }

    @Override
    public Optional<Achievement> recordVerifiedPass(UUID teamId, UUID checkpointId, Instant passTime) {
        Checkpoint checkpoint = checkpointRepository.findById(checkpointId)
            .orElseThrow(() -> new IllegalArgumentException("Checkpoint not found: " + checkpointId));

        // Automatic geofenced passes are gated by the checkpoint's opening window:
        // a pass outside that window earns no points and fires no automations, as if
        // the boat never reached the checkpoint. Manual operator verification is an
        // override (handled by verifyAchievement) and is deliberately not gated here.
        if (!checkpoint.isOpenAt(passTime)) {
            Log.debugf("Skipping out-of-window automatic pass team=%s checkpoint=%s at %s",
                teamId, checkpointId, passTime);
            return Optional.empty();
        }

        Optional<Achievement> existing = achievementRepository.findByTeamIdAndCheckpointId(teamId, checkpointId);
        if (existing.isPresent()) {
            Achievement achievement = existing.get();
            if (achievement.getStatus() != AchievementStatus.PENDING) {
                // Already VERIFIED or REJECTED — preserve operator state (notes, bonus points).
                return Optional.empty();
            }
            achievement.setStatus(AchievementStatus.VERIFIED);
            achievement.setVerifiedAt(passTime);
            // Leave verifiedBy null and preserve existing bonusPoints/notes set during proximity detection.

            int total = checkpoint.getPoints() + achievement.getBonusPoints();

            Achievement saved = achievementRepository.save(achievement);

            Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalStateException("Team not found: " + teamId));

            pointsLedgerService.createEntry(
                team.getId(),
                team.getGameId(),
                PointEntryType.CHECKPOINT_VERIFICATION,
                total,
                resolveCheckpointLocation(checkpoint),
                "Checkpoint " + checkpoint.getName() + " geverifieerd (automatisch)",
                saved.getId(),
                null
            );
            automationEngine.onCheckpointPass(team.getGameId(), team.getId(), checkpointId, passTime);
            return Optional.of(saved);
        }

        // No prior record — create a VERIFIED achievement directly.
        Team team = teamRepository.findById(teamId)
            .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));

        Achievement achievement = new Achievement();
        achievement.setTeamId(teamId);
        achievement.setCheckpointId(checkpointId);
        achievement.setStatus(AchievementStatus.VERIFIED);
        achievement.setDetectedAt(passTime);
        achievement.setVerifiedAt(passTime);
        achievement.setBonusPoints(0);

        Achievement saved = achievementRepository.save(achievement);

        pointsLedgerService.createEntry(
            team.getId(),
            team.getGameId(),
            PointEntryType.CHECKPOINT_VERIFICATION,
            checkpoint.getPoints(),
            resolveCheckpointLocation(checkpoint),
            "Checkpoint " + checkpoint.getName() + " geverifieerd (automatisch)",
            saved.getId(),
            null
        );

        automationEngine.onCheckpointPass(team.getGameId(), team.getId(), checkpointId, passTime);

        return Optional.of(saved);
    }

    @Override
    public Optional<Achievement> rejectAchievement(UUID achievementId, UUID gameMasterId, String notes) {
        Optional<Achievement> optAchievement = achievementRepository.findById(achievementId);
        if (optAchievement.isEmpty()) {
            return Optional.empty();
        }

        Achievement achievement = optAchievement.get();

        // Can only reject PENDING achievements
        if (achievement.getStatus() != AchievementStatus.PENDING) {
            return Optional.empty();
        }

        // Update achievement status (no points awarded)
        achievement.setStatus(AchievementStatus.REJECTED);
        achievement.setVerifiedAt(Instant.now());
        achievement.setVerifiedBy(gameMasterId);
        achievement.setNotes(notes);

        return Optional.of(achievementRepository.save(achievement));
    }

    @Override
    public List<CheckpointTeamResponse> getTeamsNearCheckpoint(UUID gameId, UUID checkpointId) {
        // Hide stale rejections from the operator view (and free those teams for re-detection)
        purgeExpiredRejections();

        Checkpoint checkpoint = checkpointRepository.findById(checkpointId)
            .orElseThrow(() -> new IllegalArgumentException("Checkpoint not found: " + checkpointId));

        // Map of teamId → team name from all teams in the game
        Map<UUID, String> teamNames = new HashMap<>();
        teamRepository.findByGameId(gameId).forEach(t -> teamNames.put(t.getId(), t.getName()));

        // Operator view = the achievements at this checkpoint. Proximity detection
        // (creating PENDING achievements) is handled outside this service — by the
        // location polling worker, or by future Traccar-style location updates.
        return achievementRepository.findByCheckpointId(checkpointId).stream()
            .map(ach -> new CheckpointTeamResponse(
                ach.getTeamId(),
                teamNames.getOrDefault(ach.getTeamId(), ach.getTeamId().toString()),
                ach.getDetectedAt() != null ? ach.getDetectedAt().toString() : null,
                ach.getStatus().name(),
                ach.getId(),
                checkpoint.getPoints(),
                computePointsEarned(ach, checkpoint),
                ach.getVerifiedAt() != null ? ach.getVerifiedAt().toString() : null,
                ach.getNotes()
            ))
            .sorted((a, b) -> {
                int byStatus = statusOrder(a.achievementStatus()) - statusOrder(b.achievementStatus());
                if (byStatus != 0) return byStatus;
                // Within PENDING: oldest first (FIFO). Within VERIFIED/REJECTED: most recent first.
                String aTime = a.detectedAt() != null ? a.detectedAt() : "";
                String bTime = b.detectedAt() != null ? b.detectedAt() : "";
                return "PENDING".equals(a.achievementStatus())
                    ? aTime.compareTo(bTime)
                    : bTime.compareTo(aTime);
            })
            .toList();
    }

    private int statusOrder(String status) {
        if ("PENDING".equals(status)) return 0;
        if (status == null) return 1;
        return 2; // VERIFIED or REJECTED
    }

    /**
     * Points earned for a given achievement: base value of the checkpoint plus any
     * bonus, but only when the achievement is VERIFIED. Otherwise null.
     */
    private static Integer computePointsEarned(Achievement ach, Checkpoint cp) {
        if (ach == null || ach.getStatus() != AchievementStatus.VERIFIED) {
            return null;
        }
        return cp.getPoints() + ach.getBonusPoints();
    }

    @Override
    public Achievement save(Achievement achievement) {
        return achievementRepository.save(achievement);
    }

    @Override
    public void delete(UUID id) {
        achievementRepository.delete(id);
    }

    @Override
    public Integer computePointsEarned(Achievement achievement) {
        if (achievement == null || achievement.getStatus() != AchievementStatus.VERIFIED) {
            return null;
        }
        Checkpoint cp = checkpointRepository.findById(achievement.getCheckpointId()).orElse(null);
        if (cp == null) return null;
        return cp.getPoints() + achievement.getBonusPoints();
    }
}
