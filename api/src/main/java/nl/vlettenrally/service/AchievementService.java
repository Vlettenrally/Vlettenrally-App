package nl.vlettenrally.service;

import nl.vlettenrally.domain.Achievement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for Achievement operations.
 * 
 * Achievement Flow:
 * 1. System detects team proximity → detectProximity() creates PENDING achievement
 * 2. Game master receives notification
 * 3. Game master calls verifyAchievement() or rejectAchievement()
 * 4. Points awarded only on verification
 */
public interface AchievementService {
    
    Optional<Achievement> findById(UUID id);
    
    List<Achievement> findAll();
    
    List<Achievement> findByTeamId(UUID teamId);
    
    List<Achievement> findByCheckpointId(UUID checkpointId);
    
    Optional<Achievement> findByTeamIdAndCheckpointId(UUID teamId, UUID checkpointId);
    
    /**
     * Get all pending achievements for a checkpoint (for game masters)
     */
    List<Achievement> findPendingByCheckpointId(UUID checkpointId);
    
    /**
     * Get all pending achievements across all checkpoints
     */
    List<Achievement> findAllPending();
    
    /**
     * System detects team proximity to checkpoint.
     * Creates a PENDING achievement and notifies game masters.
     * @return The created pending achievement, or empty if already detected/verified
     */
    Optional<Achievement> detectProximity(UUID teamId, UUID checkpointId);
    
    /**
     * Game master verifies a pending achievement.
     * Awards points to the team.
     * @param achievementId The pending achievement to verify
     * @param gameMasterId The game master performing verification (optional for now)
     * @param notes Optional notes
     * @return The verified achievement, or empty if not found/already processed
     */
    Optional<Achievement> verifyAchievement(UUID achievementId, UUID gameMasterId, String notes);
    
    /**
     * Game master rejects a pending achievement.
     * No points awarded.
     * @param achievementId The pending achievement to reject
     * @param gameMasterId The game master performing rejection (optional for now)
     * @param notes Reason for rejection
     * @return The rejected achievement, or empty if not found/already processed
     */
    Optional<Achievement> rejectAchievement(UUID achievementId, UUID gameMasterId, String notes);
    
    Achievement save(Achievement achievement);
    
    void delete(UUID id);
}
