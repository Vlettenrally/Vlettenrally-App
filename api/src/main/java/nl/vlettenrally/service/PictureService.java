package nl.vlettenrally.service;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

import jakarta.ws.rs.core.Response;
import nl.vlettenrally.data.domain.Picture;
import nl.vlettenrally.data.dto.picture.PictureResponse;
import nl.vlettenrally.data.dto.picture.PublicPictureResponse;
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
     * Reserve an upload slot for an admin and return a pre-signed PUT URL.
     * Uses the current active game; no per-team cap applies.
     *
     * @throws PictureException 415 if the content type is not allowed, 409 if no game is active.
     */
    UploadTicket requestAdminUpload(UUID adminId, String contentType);

    /**
     * Confirm an admin upload. Validates the stored object and auto-approves the picture.
     *
     * @throws PictureException 404 if not found, 409 if no object was uploaded,
     *                          422 if the object violates size/type limits.
     */
    Picture confirmAdminUpload(UUID pictureId, UUID adminId);

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
     * One page of the moderation queue, oldest first. {@code gameId} null means all games.
     *
     * @throws PictureException 400 if the cursor is malformed.
     */
    PicturePage findReadyForModerationPage(UUID gameId, Cursor cursor, int pageSize);

    /** One page of pictures filtered by status, newest first. Null {@code status}/{@code gameId} means unfiltered. */
    PicturePage findPage(PictureStatus status, UUID gameId, Cursor cursor, int pageSize);

    /** One page of approved pictures for a game, newest first. */
    PicturePage findApprovedPage(UUID gameId, Cursor cursor, int pageSize);

    /** Approved pictures for a game, newest first (public gallery). */
    List<Picture> findApprovedByGame(UUID gameId);

    /** Every approved picture across all games, newest first (public gallery). */
    List<Picture> findAllApproved();

    /**
     * One page of approved pictures for the game a team belongs to, newest first. Powers the
     * players' photowall: a team sees every team's approved pictures for its own game (the game
     * is resolved server-side, so a team cannot request another game's pictures).
     *
     * @throws PictureException 404 if the team does not exist.
     */
    PicturePage findApprovedForTeamGamePage(UUID teamId, Cursor cursor, int pageSize);

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

    /**
     * File name for a game's approved-picture archive, e.g. {@code fotos-vlettenrally-2026.zip}.
     * Falls back to the game id when the game has no usable name.
     */
    String approvedArchiveName(UUID gameId);

    /**
     * Stream every approved, uploaded picture of a game into {@code out} as a zip. Entries are
     * named after the uploading team ({@code de-watervlooien-01.jpg}); admin uploads are grouped
     * under {@code organisatie}. Objects are copied one at a time, so neither the archive nor any
     * single picture is held whole in memory.
     * <p>
     * A picture whose stored object is missing or unreadable is skipped with a warning rather
     * than failing the whole archive: the response has already been committed by the time the
     * first byte is written, so aborting would leave the client with a truncated zip.
     */
    void writeApprovedArchive(UUID gameId, OutputStream out) throws IOException;

    /** A short-lived pre-signed view URL for an uploaded picture, or null if not uploaded. */
    String viewUrlFor(Picture picture);

    /** Configured maximum upload size in bytes. */
    long maxBytes();

    /** Configured list of allowed content types. */
    List<String> allowedContentTypes();

    /**
     * Build the admin/team-own response for a picture, resolving its owning team (for
     * name/description) and a fresh view URL. Team fields are null for admin uploads.
     */
    PictureResponse toResponse(Picture picture);

    /** Batch variant of {@link #toResponse}; resolves each distinct team at most once. */
    List<PictureResponse> toResponses(List<Picture> pictures);

    /**
     * Build the minimal public-gallery response for a picture, attributing it to the uploading
     * team by name/description (null for admin uploads).
     */
    PublicPictureResponse toPublicResponse(Picture picture);

    /** Batch variant of {@link #toPublicResponse}; resolves each distinct team at most once. */
    List<PublicPictureResponse> toPublicResponses(List<Picture> pictures);

    /** Result of {@link #requestUpload}. */
    record UploadTicket(Picture picture, String uploadUrl, Instant expiresAt) {}

    /**
     * Position in a keyset-paged picture list: the sort key and id of the last row of the
     * previous page. Which timestamp {@code at} refers to depends on the list being paged
     * (upload time for the moderation queue and approved lists, creation time otherwise) —
     * callers only ever echo back the cursor the previous page handed them.
     */
    record Cursor(Instant at, UUID id) {

        /**
         * Parse the cursor a client sent back. Both halves must be present or absent together;
         * returns null (meaning "first page") when neither is set.
         *
         * @throws PictureException 400 if only one half is present, or the timestamp is unparseable.
         */
        public static Cursor parse(String at, UUID id) {
            if (at == null || at.isBlank()) {
                if (id != null) {
                    throw new PictureException(Response.Status.BAD_REQUEST, "cursorId requires cursorAt");
                }
                return null;
            }
            if (id == null) {
                throw new PictureException(Response.Status.BAD_REQUEST, "cursorAt requires cursorId");
            }
            try {
                return new Cursor(Instant.parse(at.trim()), id);
            } catch (DateTimeParseException e) {
                throw new PictureException(Response.Status.BAD_REQUEST, "Invalid cursorAt: " + at);
            }
        }
    }

    /**
     * One page of pictures plus the cursor that fetches the next one.
     * <p>
     * {@code hasMore} is derived by asking the repository for one row more than the page size,
     * so no separate count query is needed — and none is wanted: these lists mutate while the
     * client pages through them, which is exactly why they are keyset- rather than offset-paged.
     */
    record PicturePage(List<Picture> pictures, boolean hasMore, Cursor next) {}
}
