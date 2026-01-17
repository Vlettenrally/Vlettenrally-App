package nl.vlettenrally.service.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.domain.Achievement;
import nl.vlettenrally.domain.Achievement.Status;
import nl.vlettenrally.domain.Checkpoint;
import nl.vlettenrally.domain.Team;
import nl.vlettenrally.repository.AchievementRepository;
import nl.vlettenrally.repository.CheckpointRepository;
import nl.vlettenrally.repository.TeamRepository;
import nl.vlettenrally.service.AchievementService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of AchievementService.
 * 
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
            .filter(a -> a.getStatus() == Status.PENDING)
            .toList();
    }

    @Override
    public List<Achievement> findAllPending() {
        return achievementRepository.findAll().stream()
            .filter(a -> a.getStatus() == Status.PENDING)
            .toList();
    }

    @Override
    public Optional<Achievement> detectProximity(UUID teamId, UUID checkpointId) {
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

        // Create PENDING achievement
        Achievement achievement = new Achievement();
        achievement.setTeamId(teamId);
        achievement.setCheckpointId(checkpointId);
        achievement.setPointsEarned(checkpoint.getPoints()); // Points to be awarded upon verification
        achievement.setStatus(Status.PENDING);
        achievement.setDetectedAt(Instant.now());

        Achievement saved = achievementRepository.save(achievement);
        
        // TODO: Send notification to game masters at this checkpoint
        // This could be via WebSocket, push notification, etc.
        
        return Optional.of(saved);
    }

    @Override
    public Optional<Achievement> verifyAchievement(UUID achievementId, UUID gameMasterId, String notes) {
        Optional<Achievement> optAchievement = achievementRepository.findById(achievementId);
        if (optAchievement.isEmpty()) {
            return Optional.empty();
        }

        Achievement achievement = optAchievement.get();
        
        // Can only verify PENDING achievements
        if (achievement.getStatus() != Status.PENDING) {
            return Optional.empty();
        }

        // Update achievement status
        achievement.setStatus(Status.VERIFIED);
        achievement.setVerifiedAt(Instant.now());
        achievement.setVerifiedBy(gameMasterId);
        achievement.setNotes(notes);

        // Award points to team
        Team team = teamRepository.findById(achievement.getTeamId())
            .orElseThrow(() -> new IllegalStateException("Team not found: " + achievement.getTeamId()));
        
        team.setPoints(team.getPoints() + achievement.getPointsEarned());
        team.setUpdatedAt(Instant.now());
        teamRepository.save(team);

        return Optional.of(achievementRepository.save(achievement));
    }

    @Override
    public Optional<Achievement> rejectAchievement(UUID achievementId, UUID gameMasterId, String notes) {
        Optional<Achievement> optAchievement = achievementRepository.findById(achievementId);
        if (optAchievement.isEmpty()) {
            return Optional.empty();
        }

        Achievement achievement = optAchievement.get();
        
        // Can only reject PENDING achievements
        if (achievement.getStatus() != Status.PENDING) {
            return Optional.empty();
        }

        // Update achievement status (no points awarded)
        achievement.setStatus(Status.REJECTED);
        achievement.setVerifiedAt(Instant.now());
        achievement.setVerifiedBy(gameMasterId);
        achievement.setNotes(notes);
        achievement.setPointsEarned(0); // Clear points since rejected

        return Optional.of(achievementRepository.save(achievement));
    }

    @Override
    public Achievement save(Achievement achievement) {
        return achievementRepository.save(achievement);
    }

    @Override
    public void delete(UUID id) {
        achievementRepository.delete(id);
    }
}
