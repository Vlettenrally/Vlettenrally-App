package nl.vlettenrally.api.v1;

import io.quarkus.logging.Log;
import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.StreamingOutput;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import nl.vlettenrally.api.security.SecurityRoles;
import nl.vlettenrally.api.security.TeamScoped;
import nl.vlettenrally.api.security.TeamScopedOrAdmin;
import nl.vlettenrally.data.domain.Picture;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.data.dto.picture.PictureDownloadTokenResponse;
import nl.vlettenrally.data.dto.picture.PictureListResponse;
import nl.vlettenrally.data.dto.picture.PictureRejectRequest;
import nl.vlettenrally.data.dto.picture.PictureUploadRequest;
import nl.vlettenrally.data.dto.picture.PictureUploadResponse;
import nl.vlettenrally.data.dto.picture.PublicPictureListResponse;
import nl.vlettenrally.data.dto.picture.PublicPictureResponse;
import nl.vlettenrally.data.enums.PictureStatus;
import nl.vlettenrally.service.PictureDownloadTokenService;
import nl.vlettenrally.service.PictureException;
import nl.vlettenrally.service.PictureService;
import nl.vlettenrally.service.PictureService.Cursor;
import nl.vlettenrally.service.PictureService.PicturePage;

import java.util.Optional;
import java.util.UUID;

/**
 * REST Resource for team picture uploads and admin moderation.
 * <p>
 * Teams upload directly to S3 via a pre-signed URL:
 * 1. POST /teams/{teamId}/pictures            → reserve a slot, get a pre-signed PUT URL
 * 2. (browser PUTs the file to that URL)
 * 3. POST /teams/{teamId}/pictures/{id}/confirm → validate + mark uploaded
 * <p>
 * Admins moderate: GET /pictures/pending, POST /pictures/{id}/approve, /reject, and can pull a
 * game's approved pictures as a zip via POST /pictures/download-token + GET /pictures/approved/download.
 */
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PictureResource {

    @Inject
    PictureService pictureService;

    @Inject
    PictureDownloadTokenService downloadTokenService;

    /** Default batch for the admin lists; matches the frontend's grid so a page fills the viewport. */
    private static final int DEFAULT_PAGE_SIZE = 24;
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * Default batch for the players' photowall. Historically a hard cap on the whole wall; now
     * that the wall scrolls, it is just the first batch size.
     */
    @ConfigProperty(name = "vlettenrally.pictures.photowall-limit")
    int photowallPageSize;

    // ==================== Team Endpoints ====================

    /**
     * Reserve an upload slot and get a pre-signed PUT URL for a single picture.
     */
    @POST
    @Path("/teams/{teamId}/pictures")
    @RolesAllowed("team")
    @TeamScoped
    public Response requestUpload(@PathParam("teamId") UUID teamId, PictureUploadRequest request) {
        if (request == null || request.contentType() == null || request.contentType().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("contentType is required"))
                .build();
        }
        try {
            PictureService.UploadTicket ticket = pictureService.requestUpload(teamId, request.contentType());
            Picture p = ticket.picture();
            PictureUploadResponse body = new PictureUploadResponse(
                p.getId(),
                p.getObjectKey(),
                ticket.uploadUrl(),
                p.getContentType(),
                ticket.expiresAt().toString(),
                pictureService.maxBytes(),
                pictureService.allowedContentTypes()
            );
            return Response.status(Response.Status.CREATED).entity(body).build();
        } catch (PictureException e) {
            return error(e);
        }
    }

    /**
     * Confirm a finished upload; validates the object and marks it ready for moderation.
     */
    @POST
    @Path("/teams/{teamId}/pictures/{pictureId}/confirm")
    @RolesAllowed("team")
    @TeamScoped
    public Response confirmUpload(@PathParam("teamId") UUID teamId, @PathParam("pictureId") UUID pictureId) {
        try {
            return Response.ok(pictureService.toResponse(pictureService.confirmUpload(teamId, pictureId))).build();
        } catch (PictureException e) {
            return error(e);
        }
    }

    /**
     * List a team's own pictures (admins may view any team's).
     */
    @GET
    @Path("/teams/{teamId}/pictures")
    @RolesAllowed({SecurityRoles.STAFF, SecurityRoles.TEAM})
    @TeamScopedOrAdmin
    public Response getTeamPictures(@PathParam("teamId") UUID teamId) {
        return Response.ok(pictureService.toResponses(pictureService.findByTeamId(teamId))).build();
    }

    /**
     * The players' photowall: all approved pictures for the team's game, newest first.
     * The game is resolved server-side from the team, so a team only ever sees its own game.
     * <p>
     * Returns the minimal {@link PublicPictureResponse} projection: this is a shared wall of
     * every team's photos, so one team must not see another team's moderation metadata
     * (status, notes, moderator).
     */
    @GET
    @Path("/teams/{teamId}/pictures/approved")
    @RolesAllowed({SecurityRoles.STAFF, SecurityRoles.TEAM})
    @TeamScopedOrAdmin
    public Response getPhotowall(@PathParam("teamId") UUID teamId,
                                 @QueryParam("cursorAt") String cursorAt,
                                 @QueryParam("cursorId") UUID cursorId,
                                 @QueryParam("pageSize") Integer pageSize) {
        try {
            PicturePage page = pictureService.findApprovedForTeamGamePage(
                teamId, Cursor.parse(cursorAt, cursorId), pageSize(pageSize, photowallPageSize));
            return Response.ok(new PublicPictureListResponse(
                pictureService.toPublicResponses(page.pictures()),
                cursorAt(page), cursorId(page), page.hasMore())).build();
        } catch (PictureException e) {
            return error(e);
        }
    }

    /**
     * Delete a team's own pending picture (frees a slot).
     */
    @DELETE
    @Path("/teams/{teamId}/pictures/{pictureId}")
    @RolesAllowed("team")
    @TeamScoped
    public Response deletePicture(@PathParam("teamId") UUID teamId, @PathParam("pictureId") UUID pictureId) {
        try {
            pictureService.deleteOwnPicture(teamId, pictureId);
            return Response.noContent().build();
        } catch (PictureException e) {
            return error(e);
        }
    }

    // ==================== Admin Endpoints ====================

    /**
     * Reserve an upload slot for an admin and get a pre-signed PUT URL. Auto-approved on confirm.
     */
    @POST
    @Path("/admin/pictures")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.SUPER_ADMIN, SecurityRoles.GAME_MASTER,
                   SecurityRoles.MESSAGE_SENDER, SecurityRoles.CHECKPOINT_MANAGER, SecurityRoles.RALLY_DESK})
    public Response requestAdminUpload(PictureUploadRequest request, @Context SecurityContext securityContext) {
        if (request == null || request.contentType() == null || request.contentType().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("contentType is required"))
                .build();
        }
        try {
            PictureService.UploadTicket ticket = pictureService.requestAdminUpload(adminId(securityContext), request.contentType());
            Picture p = ticket.picture();
            PictureUploadResponse body = new PictureUploadResponse(
                p.getId(),
                p.getObjectKey(),
                ticket.uploadUrl(),
                p.getContentType(),
                ticket.expiresAt().toString(),
                pictureService.maxBytes(),
                pictureService.allowedContentTypes()
            );
            return Response.status(Response.Status.CREATED).entity(body).build();
        } catch (PictureException e) {
            return error(e);
        }
    }

    /**
     * Confirm an admin upload; validates the object and auto-approves the picture.
     */
    @POST
    @Path("/admin/pictures/{pictureId}/confirm")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.SUPER_ADMIN, SecurityRoles.GAME_MASTER,
                   SecurityRoles.MESSAGE_SENDER, SecurityRoles.CHECKPOINT_MANAGER, SecurityRoles.RALLY_DESK})
    public Response confirmAdminUpload(@PathParam("pictureId") UUID pictureId, @Context SecurityContext securityContext) {
        try {
            return Response.ok(pictureService.toResponse(pictureService.confirmAdminUpload(pictureId, adminId(securityContext)))).build();
        } catch (PictureException e) {
            return error(e);
        }
    }

    /**
     * All approved pictures; visible to every admin role including read-only viewers.
     */
    @GET
    @Path("/pictures/approved")
    @RolesAllowed(SecurityRoles.STAFF)
    public Response getApprovedPictures(@QueryParam("gameId") UUID gameId,
                                        @QueryParam("cursorAt") String cursorAt,
                                        @QueryParam("cursorId") UUID cursorId,
                                        @QueryParam("pageSize") Integer pageSize) {
        try {
            return Response.ok(listResponse(pictureService.findApprovedPage(
                gameId, Cursor.parse(cursorAt, cursorId), pageSize(pageSize, DEFAULT_PAGE_SIZE)))).build();
        } catch (PictureException e) {
            return error(e);
        }
    }

    /**
     * Mint a one-time token for downloading a game's approved pictures as a zip.
     * <p>
     * The download itself is a browser navigation and so cannot carry a bearer token; this is
     * where the caller's JWT is checked instead. See {@link #downloadApprovedArchive}.
     */
    @POST
    @Path("/pictures/download-token")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.SUPER_ADMIN,
                   SecurityRoles.GAME_MASTER, SecurityRoles.RALLY_DESK})
    public Response requestDownloadToken(@QueryParam("gameId") UUID gameId) {
        if (gameId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("gameId is required"))
                .build();
        }
        try {
            String token = downloadTokenService.issue(gameId);
            return Response.ok(new PictureDownloadTokenResponse(token, downloadTokenService.ttlSeconds())).build();
        } catch (IllegalStateException e) {
            Log.errorf("Failed to issue picture download token for game %s: %s", gameId, e.getMessage());
            return Response.serverError()
                .entity(new ErrorResponse("Failed to prepare the download"))
                .build();
        }
    }

    /**
     * Stream every approved picture of a game as a zip.
     * <p>
     * {@code @PermitAll} is deliberate: this URL is opened by navigating the browser to it, so no
     * {@code Authorization} header is sent and proactive JWT authentication has nothing to verify.
     * Authorisation comes from the single-use token minted by {@link #requestDownloadToken}, which
     * also carries the game id — the caller cannot point a token at a different game.
     */
    @GET
    @Path("/pictures/approved/download")
    @PermitAll
    @Blocking
    @Produces("application/zip")
    public Response downloadApprovedArchive(@QueryParam("token") String token) {
        Optional<UUID> gameId = downloadTokenService.consume(token);
        if (gameId.isEmpty()) {
            // Explicit media type: the method produces zip, so the error body needs to say JSON.
            return Response.status(Response.Status.UNAUTHORIZED)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse("Invalid or expired download token"))
                .build();
        }

        UUID game = gameId.get();
        StreamingOutput archive = out -> pictureService.writeApprovedArchive(game, out);
        return Response.ok(archive)
            .header("Content-Disposition",
                "attachment; filename=\"" + pictureService.approvedArchiveName(game) + "\"")
            .build();
    }

    /**
     * Pictures awaiting moderation (uploaded + pending).
     */
    @GET
    @Path("/pictures/pending")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.GAME_MASTER, SecurityRoles.RALLY_DESK})
    public Response getPendingPictures(@QueryParam("gameId") UUID gameId,
                                       @QueryParam("cursorAt") String cursorAt,
                                       @QueryParam("cursorId") UUID cursorId,
                                       @QueryParam("pageSize") Integer pageSize) {
        try {
            return Response.ok(listResponse(pictureService.findReadyForModerationPage(
                gameId, Cursor.parse(cursorAt, cursorId), pageSize(pageSize, DEFAULT_PAGE_SIZE)))).build();
        } catch (PictureException e) {
            return error(e);
        }
    }

    /**
     * All pictures, optionally filtered by status (PENDING/APPROVED/REJECTED).
     */
    @GET
    @Path("/pictures")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.GAME_MASTER, SecurityRoles.RALLY_DESK})
    public Response getAllPictures(@QueryParam("status") String status,
                                   @QueryParam("gameId") UUID gameId,
                                   @QueryParam("cursorAt") String cursorAt,
                                   @QueryParam("cursorId") UUID cursorId,
                                   @QueryParam("pageSize") Integer pageSize) {
        PictureStatus parsed = null;
        if (status != null && !status.isBlank()) {
            try {
                parsed = PictureStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Invalid status: " + status))
                    .build();
            }
        }
        try {
            return Response.ok(listResponse(pictureService.findPage(
                parsed, gameId, Cursor.parse(cursorAt, cursorId), pageSize(pageSize, DEFAULT_PAGE_SIZE)))).build();
        } catch (PictureException e) {
            return error(e);
        }
    }

    /**
     * Approve a picture.
     */
    @POST
    @Path("/pictures/{pictureId}/approve")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.GAME_MASTER, SecurityRoles.RALLY_DESK})
    public Response approvePicture(@PathParam("pictureId") UUID pictureId, @Context SecurityContext securityContext) {
        try {
            return Response.ok(pictureService.toResponse(pictureService.approve(pictureId, adminId(securityContext)))).build();
        } catch (PictureException e) {
            return error(e);
        }
    }

    /**
     * Reject a picture (object retained for audit).
     */
    @POST
    @Path("/pictures/{pictureId}/reject")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.GAME_MASTER, SecurityRoles.RALLY_DESK})
    public Response rejectPicture(@PathParam("pictureId") UUID pictureId, PictureRejectRequest request,
                                  @Context SecurityContext securityContext) {
        String notes = request != null ? request.notes() : null;
        try {
            return Response.ok(pictureService.toResponse(pictureService.reject(pictureId, adminId(securityContext), notes))).build();
        } catch (PictureException e) {
            return error(e);
        }
    }

    // ==================== Helpers ====================

    private PictureListResponse listResponse(PicturePage page) {
        return new PictureListResponse(
            pictureService.toResponses(page.pictures()),
            cursorAt(page), cursorId(page), page.hasMore());
    }

    private static String cursorAt(PicturePage page) {
        return page.next() != null ? page.next().at().toString() : null;
    }

    private static String cursorId(PicturePage page) {
        return page.next() != null ? page.next().id().toString() : null;
    }

    /**
     * Clamp the requested page size. The upper bound matters more than usual here: every picture
     * in a response costs one S3 presign, so an unbounded page size is the exact problem paging
     * was added to solve.
     */
    private static int pageSize(Integer requested, int fallback) {
        if (requested == null || requested < 1 || requested > MAX_PAGE_SIZE) {
            return fallback;
        }
        return requested;
    }

    private static UUID adminId(SecurityContext securityContext) {
        return securityContext.getUserPrincipal() != null
            ? UUID.fromString(securityContext.getUserPrincipal().getName())
            : null;
    }

    private Response error(PictureException e) {
        return Response.status(e.getStatusCode()).entity(new ErrorResponse(e.getMessage())).build();
    }
}
