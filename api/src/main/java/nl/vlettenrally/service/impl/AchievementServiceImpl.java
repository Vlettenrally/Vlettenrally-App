package nl.vlettenrally.service.impl;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
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
import nl.vlettenrally.fcm.PushUrgency;
import nl.vlettenrally.observability.DeliveryMetrics;
import nl.vlettenrally.observability.DeliveryMetrics.FcmCaller;
import nl.vlettenrally.service.AchievementService;
import nl.vlettenrally.service.AutomationEngine;
import nl.vlettenrally.service.PointsLedgerService;
import nl.vlettenrally.service.PointsTotalsCache;
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
import nl.vlettenrally.websockets.dto.WebSocketMessageType;

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
    DeliveryMetrics deliveryMetrics;

    @Inject
    PointsLedgerService pointsLedgerService;

    @Inject
    PointsTotalsCache pointsTotalsCache;

    @Inject
    TransactionSynchronizationRegistry txRegistry;

    @Inject
    AutomationEngine automationEngine;

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @ConfigProperty(name = "vlettenrally.achievement.rejection-expiry-minutes", defaultValue = "15")
    long rejectionExpiryMinutes;

    @ConfigProperty(name = "vlettenrally.firebase.ephemeral-ttl-seconds", defaultValue = "900")
    int ephemeralTtlSeconds;

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
            Log.infof("No FCM subscribers for checkpoint %s — skipping push", checkpoint.getId());
            return;
        }

        // This path bypasses MessageTransmitter entirely, so msg.transmit never sees it.
        var adminTokens = fcmTokenRepository.findAdminTokensByAdminIds(adminIds);
        deliveryMetrics.fcmFanout(FcmCaller.CHECKPOINT_OPERATOR, adminTokens.size());
        adminTokens.forEach(fcmToken -> {
            try {
                fcmHttpClient.sendNotification(
                    fcmToken.getToken(),
                    "Team gedetecteerd",
                    teamName + " is bij " + checkpoint.getName(),
                    Map.of(
                        "type", "CHECKPOINT_DETECTION",
                        "checkpointId", checkpoint.getId().toString(),
                        "teamId", teamId.toString()
                    ),
                    // A team is standing at the checkpoint right now, so this is worth a
                    // wakeup — and worthless once they have moved on, so it expires.
                    PushUrgency.HIGH,
                    ephemeralTtlSeconds
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
        Instant verifiedAt = Instant.now();
        achievement.setStatus(AchievementStatus.VERIFIED);
        achievement.setVerifiedAt(verifiedAt);
        achievement.setVerifiedBy(gameMasterId);
        achievement.setNotes(notes);
        achievement.setBonusPoints(bonusPoints);

        // Award (current checkpoint base + bonus) to the team's total via the ledger
        Checkpoint checkpoint = checkpointRepository.findById(achievement.getCheckpointId())
            .orElseThrow(() -> new IllegalStateException("Checkpoint not found: " + achievement.getCheckpointId()));

        return Optional.of(awardCheckpointPoints(achievement, checkpoint,
            "Checkpoint " + checkpoint.getName() + " geverifieerd",
            verifiedAt, inZoneAt(achievement, verifiedAt)));
    }

    /**
     * When the team was actually inside the checkpoint zone: detectedAt if we have it,
     * otherwise the fallback. Operator confirmations carry a verifiedAt that is merely
     * when the game master got around to it, which is not what automations should see.
     */
    private static Instant inZoneAt(Achievement achievement, Instant fallback) {
        return achievement.getDetectedAt() != null ? achievement.getDetectedAt() : fallback;
    }

    /**
     * Persist an achievement that has just been flipped to VERIFIED and pay out for it:
     * points ledger entry, live team notification, and automation triggers. Every path
     * that verifies an achievement routes through here so the payout stays identical.
     *
     * @param ledgerDescription Human-readable reason recorded on the ledger entry
     * @param achievedAt        Timestamp reported to the team's own live update
     * @param automationTime    When the team was in the zone, for automation rules
     */
    private Achievement awardCheckpointPoints(Achievement achievement, Checkpoint checkpoint,
                                              String ledgerDescription, Instant achievedAt, Instant automationTime) {
        int total = checkpoint.getPoints() + achievement.getBonusPoints();

        // Save the achievement before the ledger entry so totals stay consistent on a rollback
        Achievement saved = achievementRepository.save(achievement);

        Team team = teamRepository.findById(saved.getTeamId())
            .orElseThrow(() -> new IllegalStateException("Team not found: " + saved.getTeamId()));

        int oldTotal = pointsTotalsCache.get(team.getId());

        pointsLedgerService.createEntry(
            team.getId(),
            team.getGameId(),
            PointEntryType.CHECKPOINT_VERIFICATION,
            total,
            resolveCheckpointLocation(checkpoint),
            ledgerDescription,
            saved.getId(),
            null
        );

        notifyTeamAchievementAfterCommit(team.getGameId(), team.getId(), Map.of(
            "id", saved.getId(),
            "teamId", team.getId(),
            "checkpointId", checkpoint.getId(),
            "pointsEarned", total,
            "achievedAt", achievedAt.toString(),
            "totalPoints", oldTotal + total
        ));

        automationEngine.onCheckpointPass(team.getGameId(), team.getId(), checkpoint.getId(), automationTime);

        return saved;
    }

    /**
     * Push a live TEAM_ACHIEVEMENT event to the achieving team's own session once the
     * enclosing transaction commits, so the player's Dashboard (points + checkpoints
     * reached) updates without waiting for a reconnect-triggered resync. Falls back to
     * an immediate send when there's no active transaction (e.g. the caller isn't
     * itself @Transactional and createEntry already committed its own).
     */
    private void notifyTeamAchievementAfterCommit(UUID gameId, UUID teamId, Map<String, Object> payload) {
        try {
            txRegistry.registerInterposedSynchronization(new Synchronization() {
                @Override public void beforeCompletion() { }
                @Override public void afterCompletion(int status) {
                    if (status == Status.STATUS_COMMITTED) {
                        webSocketPublisher.sendToTeam(
                            gameId, teamId, WebSocketMessageType.TEAM_ACHIEVEMENT.getValue(), payload);
                    }
                }
            });
        } catch (IllegalStateException noTx) {
            webSocketPublisher.sendToTeam(
                gameId, teamId, WebSocketMessageType.TEAM_ACHIEVEMENT.getValue(), payload);
        }
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

            return Optional.of(awardCheckpointPoints(achievement, checkpoint,
                "Checkpoint " + checkpoint.getName() + " geverifieerd (automatisch)",
                passTime, passTime));
        }

        // No prior record — create a VERIFIED achievement directly.
        teamRepository.findById(teamId)
            .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));

        Achievement achievement = new Achievement();
        achievement.setTeamId(teamId);
        achievement.setCheckpointId(checkpointId);
        achievement.setStatus(AchievementStatus.VERIFIED);
        achievement.setDetectedAt(passTime);
        achievement.setVerifiedAt(passTime);
        achievement.setBonusPoints(0);

        return Optional.of(awardCheckpointPoints(achievement, checkpoint,
            "Checkpoint " + checkpoint.getName() + " geverifieerd (automatisch)",
            passTime, passTime));
    }

    @Override
    public Optional<Achievement> recordManualPass(UUID teamId, UUID checkpointId, UUID gameMasterId) {
        // Drop stale rejections first, so an expired one is deleted rather than promoted
        purgeExpiredRejections();

        Checkpoint checkpoint = checkpointRepository.findById(checkpointId)
            .orElseThrow(() -> new IllegalArgumentException("Checkpoint not found: " + checkpointId));

        teamRepository.findById(teamId)
            .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));

        // The operator is standing at the checkpoint looking at the boat, so this is an
        // override: it is deliberately not gated by the checkpoint's opening window, and
        // it wins over an earlier PENDING detection or REJECTED call.
        Instant now = Instant.now();
        Achievement achievement = achievementRepository.findByTeamIdAndCheckpointId(teamId, checkpointId)
            .orElse(null);

        if (achievement != null) {
            if (achievement.getStatus() == AchievementStatus.VERIFIED) {
                // Adding a team that is already verified is a no-op, never a second payout.
                return Optional.of(achievement);
            }
            if (achievement.getStatus() == AchievementStatus.REJECTED) {
                // The notes hold the rejection reason, which no longer applies.
                achievement.setNotes(null);
            }
        } else {
            achievement = new Achievement();
            achievement.setTeamId(teamId);
            achievement.setCheckpointId(checkpointId);
            achievement.setDetectedAt(now);
            achievement.setBonusPoints(0);
        }

        achievement.setStatus(AchievementStatus.VERIFIED);
        achievement.setVerifiedAt(now);
        achievement.setVerifiedBy(gameMasterId);

        return Optional.of(awardCheckpointPoints(achievement, checkpoint,
            "Checkpoint " + checkpoint.getName() + " geverifieerd (handmatig)",
            now, inZoneAt(achievement, now)));
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
