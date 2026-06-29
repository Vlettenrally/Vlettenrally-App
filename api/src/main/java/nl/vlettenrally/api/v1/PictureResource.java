package nl.vlettenrally.api.v1;

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
import nl.vlettenrally.api.security.SecurityRoles;
import nl.vlettenrally.api.security.TeamScoped;
import nl.vlettenrally.api.security.TeamScopedOrAdmin;
import nl.vlettenrally.data.domain.Picture;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.data.dto.picture.PictureRejectRequest;
import nl.vlettenrally.data.dto.picture.PictureResponse;
import nl.vlettenrally.data.dto.picture.PictureUploadRequest;
import nl.vlettenrally.data.dto.picture.PictureUploadResponse;
import nl.vlettenrally.data.enums.PictureStatus;
import nl.vlettenrally.service.PictureException;
import nl.vlettenrally.service.PictureService;

import java.util.List;
import java.util.UUID;

/**
 * REST Resource for team picture uploads and admin moderation.
 * <p>
 * Teams upload directly to S3 via a pre-signed URL:
 * 1. POST /teams/{teamId}/pictures            → reserve a slot, get a pre-signed PUT URL
 * 2. (browser PUTs the file to that URL)
 * 3. POST /teams/{teamId}/pictures/{id}/confirm → validate + mark uploaded
 * <p>
 * Admins moderate: GET /pictures/pending, POST /pictures/{id}/approve, /reject.
 */
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PictureResource {

    @Inject
    PictureService pictureService;

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
            return Response.ok(toDto(pictureService.confirmUpload(teamId, pictureId))).build();
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
        return Response.ok(pictureService.findByTeamId(teamId).stream().map(this::toDto).toList()).build();
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
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.GAME_MASTER, SecurityRoles.RALLY_DESK})
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
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.GAME_MASTER, SecurityRoles.RALLY_DESK})
    public Response confirmAdminUpload(@PathParam("pictureId") UUID pictureId, @Context SecurityContext securityContext) {
        try {
            return Response.ok(toDto(pictureService.confirmAdminUpload(pictureId, adminId(securityContext)))).build();
        } catch (PictureException e) {
            return error(e);
        }
    }

    /**
     * Pictures awaiting moderation (uploaded + pending).
     */
    @GET
    @Path("/pictures/pending")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.GAME_MASTER, SecurityRoles.RALLY_DESK})
    public Response getPendingPictures() {
        return Response.ok(pictureService.findReadyForModeration().stream().map(this::toDto).toList()).build();
    }

    /**
     * All pictures, optionally filtered by status (PENDING/APPROVED/REJECTED).
     */
    @GET
    @Path("/pictures")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.GAME_MASTER, SecurityRoles.RALLY_DESK})
    public Response getAllPictures(@QueryParam("status") String status) {
        List<Picture> pictures;
        if (status != null && !status.isBlank()) {
            PictureStatus parsed;
            try {
                parsed = PictureStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Invalid status: " + status))
                    .build();
            }
            pictures = pictureService.findByStatus(parsed);
        } else {
            pictures = pictureService.findAll();
        }
        return Response.ok(pictures.stream().map(this::toDto).toList()).build();
    }

    /**
     * Approve a picture.
     */
    @POST
    @Path("/pictures/{pictureId}/approve")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.GAME_MASTER, SecurityRoles.RALLY_DESK})
    public Response approvePicture(@PathParam("pictureId") UUID pictureId, @Context SecurityContext securityContext) {
        try {
            return Response.ok(toDto(pictureService.approve(pictureId, adminId(securityContext)))).build();
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
            return Response.ok(toDto(pictureService.reject(pictureId, adminId(securityContext), notes))).build();
        } catch (PictureException e) {
            return error(e);
        }
    }

    // ==================== Helpers ====================

    private static UUID adminId(SecurityContext securityContext) {
        return securityContext.getUserPrincipal() != null
            ? UUID.fromString(securityContext.getUserPrincipal().getName())
            : null;
    }

    private Response error(PictureException e) {
        return Response.status(e.getStatusCode()).entity(new ErrorResponse(e.getMessage())).build();
    }

    private PictureResponse toDto(Picture p) {
        return PictureResponse.from(p, pictureService.viewUrlFor(p));
    }
}
