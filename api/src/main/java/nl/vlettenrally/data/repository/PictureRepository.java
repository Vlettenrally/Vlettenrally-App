package nl.vlettenrally.data.repository;

import java.time.Instant;
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

    /** All pictures for a team, newest first. */
    List<Picture> findByTeamId(UUID teamId);

    /** Pictures of a given status, newest first. */
    List<Picture> findByStatus(PictureStatus status);

    /** Approved pictures for a game (zip archive, public gallery), newest first. */
    List<Picture> findApprovedByGameId(UUID gameId);

    /*
     * Keyset ("cursor") paging for the picture lists the UI scrolls through.
     *
     * These lists mutate under the reader while it pages: photos are inserted live during a rally,
     * and moderating one *removes* it from the pending set. Offset paging would then re-serve or —
     * worse, for the moderation queue — silently skip rows. A cursor of (sort key, id) is stable
     * under both. The id is part of the cursor purely to make the ordering total, so equal
     * timestamps cannot straddle a page boundary.
     *
     * Pass a null cursor for the first page. The cursor is exclusive: the row it names is the last
     * one of the previous page and is never returned again.
     */

    /** Moderation queue page, oldest first (uploadedAt ASC, id ASC). {@code gameId} null means all games. */
    List<Picture> findReadyForModerationPage(UUID gameId, Instant cursorAt, UUID cursorId, int limit);

    /** Approved pictures page, newest first (uploadedAt DESC, id DESC). Backs the approved tab and the photowall. */
    List<Picture> findApprovedPage(UUID gameId, Instant cursorAt, UUID cursorId, int limit);

    /** Page of any status, newest first (createdAt DESC, id DESC). Null {@code status}/{@code gameId} means unfiltered. */
    List<Picture> findPage(PictureStatus status, UUID gameId, Instant cursorAt, UUID cursorId, int limit);

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

    /**
     * Count pictures moderated by an admin. Used to decide whether that admin can be
     * deleted outright without silently dropping the moderation trail.
     */
    long countModeratedBy(UUID adminId);

    Picture save(Picture picture);

    void delete(UUID id);
}
