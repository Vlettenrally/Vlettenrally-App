package nl.vlettenrally.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import nl.vlettenrally.data.domain.Picture;
import nl.vlettenrally.data.enums.PictureStatus;

/**
 * Service for team picture uploads and admin moderation.
 * <p>
 * Upload flow:
 * 1. {@link #requestUpload} reserves a PENDING picture (with a unique object key) and
 *    returns a pre-signed PUT URL — enforcing the per-team cap and allowed content types.
 * 2. The browser uploads the file directly to S3 using that URL.
 * 3. {@link #confirmUpload} validates the uploaded object (size/type) and marks it uploaded.
 * 4. An admin {@link #approve}s or {@link #reject}s the picture.
 */
public interface PictureService {

    /**
     * Reserve an upload slot for a team and return a pre-signed PUT URL.
     *
     * @throws PictureException 415 if the content type is not allowed, 409 if the team is
     *                          at its picture cap, 404 if the team does not exist.
     */
    UploadTicket requestUpload(UUID teamId, String contentType);

    /**
     * Confirm that the browser finished uploading. Validates the stored object against
     * the size/type limits and marks the picture uploaded (ready for moderation).
     *
     * @throws PictureException 404 if not found for the team, 409 if no object was uploaded,
     *                          422 if the object violates the size/type limits.
     */
    Picture confirmUpload(UUID teamId, UUID pictureId);

    /** All pictures owned by a team. */
    List<Picture> findByTeamId(UUID teamId);

    /**
     * Delete every picture owned by a team, including its stored object. Used when a team is
     * removed: the DB rows cascade with the team, but the S3 objects would otherwise be orphaned.
     */
    void deleteAllForTeam(UUID teamId);

    /**
     * Delete a team's own picture (only while PENDING) and its object, freeing a slot.
     *
     * @throws PictureException 404 if not found for the team, 409 if it is not PENDING.
     */
    void deleteOwnPicture(UUID teamId, UUID pictureId);

    /** Uploaded pictures awaiting moderation. */
    List<Picture> findReadyForModeration();

    /** Pictures filtered by status (admin). */
    List<Picture> findByStatus(PictureStatus status);

    /** All pictures (admin). */
    List<Picture> findAll();

    /** Approved pictures for a game, newest first (public gallery). */
    List<Picture> findApprovedByGame(UUID gameId);

    /**
     * Approved pictures for the most recent game that has any approved picture, newest first.
     * Empty when no approved pictures exist. Lets a public consumer show a gallery without
     * having to pick a game.
     */
    List<Picture> findApprovedForLatestGame();

    /**
     * Approve an uploaded picture.
     *
     * @throws PictureException 404 if not found, 409 if it has not been uploaded yet.
     */
    Picture approve(UUID pictureId, UUID adminId);

    /**
     * Reject a picture (object retained for audit).
     *
     * @throws PictureException 404 if not found.
     */
    Picture reject(UUID pictureId, UUID adminId, String notes);

    /** A short-lived pre-signed view URL for an uploaded picture, or null if not uploaded. */
    String viewUrlFor(Picture picture);

    /** Configured maximum upload size in bytes. */
    long maxBytes();

    /** Configured list of allowed content types. */
    List<String> allowedContentTypes();

    /** Result of {@link #requestUpload}. */
    record UploadTicket(Picture picture, String uploadUrl, Instant expiresAt) {}
}
