package nl.vlettenrally.data.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import nl.vlettenrally.data.domain.Picture;
import nl.vlettenrally.data.enums.PictureStatus;

/**
 * Repository interface for team-uploaded pictures.
 */
public interface PictureRepository {

    Optional<Picture> findById(UUID id);

    List<Picture> findAll();

    /** All pictures for a team, newest first. */
    List<Picture> findByTeamId(UUID teamId);

    /** Pictures of a given status, newest first. */
    List<Picture> findByStatus(PictureStatus status);

    /** Uploaded pictures still awaiting moderation (uploadedAt set, status PENDING). */
    List<Picture> findReadyForModeration();

    /** Approved pictures for a game (for the future slideshow), newest first. */
    List<Picture> findApprovedByGameId(UUID gameId);

    /**
     * Count pictures that occupy one of a team's upload slots: everything except
     * REJECTED (i.e. PENDING + APPROVED, including not-yet-uploaded reservations).
     */
    long countActiveByTeamId(UUID teamId);

    /**
     * Count all pictures ever created for a team across all statuses (including REJECTED).
     * Used to enforce the per-team total upload attempt cap.
     */
    long countAllByTeamId(UUID teamId);

    Picture save(Picture picture);

    void delete(UUID id);
}
