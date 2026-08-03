package nl.vlettenrally.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import nl.vlettenrally.data.domain.Achievement;
import nl.vlettenrally.data.dto.achievement.CheckpointTeamResponse;

/**
 * Service interface for Achievement operations.
 * <p>
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
    Optional<Achievement> verifyAchievement(UUID achievementId, UUID gameMasterId, String notes, int bonusPoints);

    /**
     * Game master rejects a pending achievement.
     * No points awarded.
     * @param achievementId The pending achievement to reject
     * @param gameMasterId The game master performing rejection (optional for now)
     * @param notes Reason for rejection
     * @return The rejected achievement, or empty if not found/already processed
     */
    Optional<Achievement> rejectAchievement(UUID achievementId, UUID gameMasterId, String notes);

    /**
     * Returns all teams that are relevant for a checkpoint operator:
     * teams currently within the checkpoint zone (by latest GPS) plus
     * teams with an existing PENDING achievement for this checkpoint.
     */
    List<CheckpointTeamResponse> getTeamsNearCheckpoint(UUID gameId, UUID checkpointId);

    /**
     * Add (or subtract) bonus points on an already-VERIFIED achievement.
     * Checkpoint personnel use this to award bonus on the spot after the tracker
     * has already auto-verified the pass-through.
     *
     * @param achievementId The VERIFIED achievement to adjust
     * @param bonusDelta    Points to add to the existing bonusPoints (may be negative)
     * @param gameMasterId  Operator credit
     * @return Updated achievement, or empty if not found / not VERIFIED
     */
    Optional<Achievement> addBonusPoints(UUID achievementId, int bonusDelta, UUID gameMasterId);

    /**
     * Idempotently record an authoritative checkpoint pass from the external tracker.
     * If no achievement exists for (teamId, checkpointId), creates one VERIFIED with the
     * given time and awards points. If one exists in PENDING, promotes it to VERIFIED
     * preserving any bonus points already added. If already VERIFIED or REJECTED,
     * no-ops (preserving operator edits).
     *
     * @param teamId       Team that passed the checkpoint
     * @param checkpointId Checkpoint that was passed
     * @param passTime     First-pass time as reported by the tracker
     * @return The created or promoted achievement, or empty if no state change
     */
    Optional<Achievement> recordVerifiedPass(UUID teamId, UUID checkpointId, Instant passTime);

    /**
     * Record a checkpoint pass that an operator adds by hand at the checkpoint station,
     * because the team never auto-detected via location polling.
     * <p>
     * A manual add is an explicit human confirmation that the team is standing at the
     * checkpoint, so it always ends VERIFIED and overrides whatever was there before:
     * an existing PENDING is promoted (preserving bonus points), an existing REJECTED is
     * promoted with its rejection reason cleared. An already-VERIFIED achievement is
     * returned unchanged so a repeated add never awards points twice. Unlike
     * {@link #recordVerifiedPass}, this is not gated by the checkpoint's opening window.
     *
     * @param teamId       Team the operator is adding
     * @param checkpointId Checkpoint being manned
     * @param gameMasterId Operator credit, stored as verifiedBy
     * @return The created or promoted achievement
     * @throws IllegalArgumentException if the team or checkpoint does not exist
     */
    Optional<Achievement> recordManualPass(UUID teamId, UUID checkpointId, UUID gameMasterId);

    Achievement save(Achievement achievement);

    void delete(UUID id);

    /**
     * Computes the points earned for an achievement based on the current checkpoint
     * value plus any bonus. Returns null when the achievement is not VERIFIED.
     */
    Integer computePointsEarned(Achievement achievement);
}
