package nl.vlettenrally.data.repository.mock;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import nl.vlettenrally.data.domain.Achievement;
import nl.vlettenrally.data.enums.AchievementStatus;
import nl.vlettenrally.data.repository.AchievementRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Mock implementation of AchievementRepository with prefilled data.
 * Only active in dev and test profiles - replaced by PostgresAchievementRepository in prod.
 * 
 * Includes a mix of VERIFIED, PENDING, and REJECTED achievements for testing.
 */
@IfBuildProfile(anyOf = {"dev", "test"})
@ApplicationScoped
public class MockAchievementRepository implements AchievementRepository {

    private final Map<UUID, Achievement> achievements = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        initializeData();
    }

    public void initializeData() {
        achievements.clear();
        
        Instant now = Instant.now();

        // ==================== Team Watervlooien (verified achievements) ====================
        Achievement ach1 = createVerifiedAchievement(
            UUID.fromString("aaaa0001-0000-0000-0000-000000000001"),
            MockTeamRepository.TEAM_WATERVLOOIEN_ID,
            MockCheckpointRepository.CHECKPOINT_START_ID,
            0,
            now.minus(2, ChronoUnit.HOURS)
        );
        achievements.put(ach1.getId(), ach1);

        Achievement ach2 = createVerifiedAchievement(
            UUID.fromString("aaaa0002-0000-0000-0000-000000000002"),
            MockTeamRepository.TEAM_WATERVLOOIEN_ID,
            MockCheckpointRepository.CHECKPOINT_AMSTEL_ID,
            0,
            now.minus(90, ChronoUnit.MINUTES)
        );
        achievements.put(ach2.getId(), ach2);

        Achievement ach3 = createVerifiedAchievement(
            UUID.fromString("aaaa0003-0000-0000-0000-000000000003"),
            MockTeamRepository.TEAM_WATERVLOOIEN_ID,
            MockCheckpointRepository.CHECKPOINT_PRINSEN_ID,
            0,
            now.minus(45, ChronoUnit.MINUTES)
        );
        achievements.put(ach3.getId(), ach3);

        // Watervlooien has a PENDING achievement at Keizersgracht (awaiting verification)
        Achievement ach4 = createPendingAchievement(
            UUID.fromString("aaaa0004-0000-0000-0000-000000000004"),
            MockTeamRepository.TEAM_WATERVLOOIEN_ID,
            MockCheckpointRepository.CHECKPOINT_KEIZER_ID,
            0,
            now.minus(5, ChronoUnit.MINUTES)
        );
        achievements.put(ach4.getId(), ach4);

        // ==================== Team Windkracht (verified achievements) ====================
        Achievement ach5 = createVerifiedAchievement(
            UUID.fromString("aaaa0005-0000-0000-0000-000000000005"),
            MockTeamRepository.TEAM_WINDKRACHT_ID,
            MockCheckpointRepository.CHECKPOINT_START_ID,
            0,
            now.minus(2, ChronoUnit.HOURS)
        );
        achievements.put(ach5.getId(), ach5);

        Achievement ach6 = createVerifiedAchievement(
            UUID.fromString("aaaa0006-0000-0000-0000-000000000006"),
            MockTeamRepository.TEAM_WINDKRACHT_ID,
            MockCheckpointRepository.CHECKPOINT_AMSTEL_ID,
            0,
            now.minus(75, ChronoUnit.MINUTES)
        );
        achievements.put(ach6.getId(), ach6);

        // Windkracht has a PENDING at Prinsengracht
        Achievement ach7 = createPendingAchievement(
            UUID.fromString("aaaa0007-0000-0000-0000-000000000007"),
            MockTeamRepository.TEAM_WINDKRACHT_ID,
            MockCheckpointRepository.CHECKPOINT_PRINSEN_ID,
            0,
            now.minus(3, ChronoUnit.MINUTES)
        );
        achievements.put(ach7.getId(), ach7);

        // ==================== Team Gorillas ====================
        Achievement ach8 = createVerifiedAchievement(
            UUID.fromString("aaaa0008-0000-0000-0000-000000000008"),
            MockTeamRepository.TEAM_GORILLAS_ID,
            MockCheckpointRepository.CHECKPOINT_START_ID,
            0,
            now.minus(2, ChronoUnit.HOURS)
        );
        achievements.put(ach8.getId(), ach8);

        Achievement ach9 = createVerifiedAchievement(
            UUID.fromString("aaaa0009-0000-0000-0000-000000000009"),
            MockTeamRepository.TEAM_GORILLAS_ID,
            MockCheckpointRepository.CHECKPOINT_AMSTEL_ID,
            0,
            now.minus(60, ChronoUnit.MINUTES)
        );
        achievements.put(ach9.getId(), ach9);

        Achievement ach10 = createVerifiedAchievement(
            UUID.fromString("aaaa0010-0000-0000-0000-000000000010"),
            MockTeamRepository.TEAM_GORILLAS_ID,
            MockCheckpointRepository.CHECKPOINT_PRINSEN_ID,
            0,
            now.minus(30, ChronoUnit.MINUTES)
        );
        achievements.put(ach10.getId(), ach10);

        // Gorillas has a REJECTED achievement (tried to skip checkpoint)
        Achievement ach11 = createRejectedAchievement(
            UUID.fromString("aaaa0011-0000-0000-0000-000000000011"),
            MockTeamRepository.TEAM_GORILLAS_ID,
            MockCheckpointRepository.CHECKPOINT_FINISH_ID,
            0,
            now.minus(20, ChronoUnit.MINUTES),
            "Team tried to go directly to finish without completing Keizersgracht"
        );
        achievements.put(ach11.getId(), ach11);

        Log.info("🎭 MockAchievementRepository initialized with " + achievements.size() + " achievements " +
            "(including " + countByStatus(AchievementStatus.PENDING) + " pending, " + countByStatus(AchievementStatus.REJECTED) + " rejected)");
    }

    // Helper methods for creating achievements with different statuses
    private Achievement createVerifiedAchievement(UUID id, UUID teamId, UUID checkpointId, int bonusPoints, Instant detectedAt) {
        Achievement a = new Achievement(id, teamId, checkpointId, bonusPoints, AchievementStatus.VERIFIED, detectedAt);
        a.setVerifiedAt(detectedAt.plus(2, ChronoUnit.MINUTES)); // Verified 2 minutes after detection
        return a;
    }

    private Achievement createPendingAchievement(UUID id, UUID teamId, UUID checkpointId, int bonusPoints, Instant detectedAt) {
        return new Achievement(id, teamId, checkpointId, bonusPoints, AchievementStatus.PENDING, detectedAt);
    }

    private Achievement createRejectedAchievement(UUID id, UUID teamId, UUID checkpointId, int bonusPoints, Instant detectedAt, String reason) {
        Achievement a = new Achievement(id, teamId, checkpointId, bonusPoints, AchievementStatus.REJECTED, detectedAt);
        a.setVerifiedAt(detectedAt.plus(1, ChronoUnit.MINUTES));
        a.setNotes(reason);
        return a;
    }

    private long countByStatus(AchievementStatus status) {
        return achievements.values().stream().filter(a -> a.getStatus() == status).count();
    }

    @Override
    public Optional<Achievement> findById(UUID id) {
        return Optional.ofNullable(achievements.get(id));
    }

    @Override
    public List<Achievement> findAll() {
        return new ArrayList<>(achievements.values());
    }

    @Override
    public List<Achievement> findByTeamId(UUID teamId) {
        return achievements.values().stream()
                .filter(a -> teamId.equals(a.getTeamId()))
                .sorted(Comparator.comparing(Achievement::getDetectedAt))
                .collect(Collectors.toList());
    }

    @Override
    public List<Achievement> findByCheckpointId(UUID checkpointId) {
        return achievements.values().stream()
                .filter(a -> checkpointId.equals(a.getCheckpointId()))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Achievement> findByTeamIdAndCheckpointId(UUID teamId, UUID checkpointId) {
        return achievements.values().stream()
                .filter(a -> teamId.equals(a.getTeamId()) && checkpointId.equals(a.getCheckpointId()))
                .findFirst();
    }

    @Override
    public Achievement save(Achievement achievement) {
        if (achievement.getId() == null) {
            achievement.setId(UUID.randomUUID());
        }
        achievements.put(achievement.getId(), achievement);
        return achievement;
    }

    @Override
    public void delete(UUID id) {
        achievements.remove(id);
    }

    @Override
    public int deleteRejectedOlderThan(java.time.Instant cutoff) {
        List<UUID> toRemove = achievements.values().stream()
                .filter(a -> a.getStatus() == AchievementStatus.REJECTED
                    && a.getVerifiedAt() != null
                    && a.getVerifiedAt().isBefore(cutoff))
                .map(Achievement::getId)
                .toList();
        toRemove.forEach(achievements::remove);
        return toRemove.size();
    }
}
