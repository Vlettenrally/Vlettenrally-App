package nl.vlettenrally.api.v1;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;
import nl.vlettenrally.api.security.SecurityRoles;
import nl.vlettenrally.api.security.TeamScopedOrAdmin;
import nl.vlettenrally.data.domain.Achievement;
import nl.vlettenrally.data.dto.achievement.AchievementResponse;
import nl.vlettenrally.data.dto.achievement.BonusPointsAdjustmentRequest;
import nl.vlettenrally.data.dto.achievement.ProximityDetectionRequest;
import nl.vlettenrally.data.dto.achievement.VerificationRequest;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.data.enums.AchievementStatus;
import nl.vlettenrally.service.AchievementService;

import java.util.List;
import java.util.UUID;

/**
 * REST Resource for Achievement operations.
 * <p>
 * Achievement Flow:
 * 1. Location polling auto-creates VERIFIED achievements via AchievementService.recordVerifiedPass();
 *    POST /api/v1/achievements/detect is the manual fallback used from the checkpoint station UI.
 * 2. Game master sees pending achievements → GET /api/v1/checkpoints/{id}/achievements/pending
 * 3. Game master verifies → POST /api/v1/achievements/{id}/verify
 * 4. Game master rejects → POST /api/v1/achievements/{id}/reject
 * <p>
 * Teams can only view their own achievements (read-only).
 */
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AchievementResource {

    @Inject
    AchievementService achievementService;

    // ==================== Team Endpoints (Read-only) ====================

    /**
     * Get all achievements for a team (teams can view their own)
     */
    @GET
    @Path("/teams/{teamId}/achievements")
    @RolesAllowed({SecurityRoles.STAFF, SecurityRoles.TEAM})
    @TeamScopedOrAdmin
    public Response getTeamAchievements(@PathParam("teamId") UUID teamId) {
        List<Achievement> achievements = achievementService.findByTeamId(teamId);
        return Response.ok(achievements.stream().map(this::toDto).toList()).build();
    }

    /**
     * Get verified achievements for a team (only completed ones with points)
     */
    @GET
    @Path("/teams/{teamId}/achievements/verified")
    @RolesAllowed({SecurityRoles.STAFF, SecurityRoles.TEAM})
    @TeamScopedOrAdmin
    public Response getTeamVerifiedAchievements(@PathParam("teamId") UUID teamId) {
        List<Achievement> achievements = achievementService.findByTeamId(teamId).stream()
            .filter(a -> a.getStatus() == AchievementStatus.VERIFIED)
            .toList();
        return Response.ok(achievements.stream().map(this::toDto).toList()).build();
    }

    // ==================== Game Master Endpoints ====================

    /**
     * Get all pending achievements (for game master dashboard)
     */
    @GET
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.CHECKPOINT_MANAGER})
    @Path("/achievements/pending")
    public Response getAllPendingAchievements() {
        List<Achievement> pending = achievementService.findAllPending();
        return Response.ok(pending.stream().map(this::toDto).toList()).build();
    }

    /**
     * Get pending achievements for a specific checkpoint (for game masters at that location)
     */
    @GET
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.CHECKPOINT_MANAGER})
    @Path("/checkpoints/{checkpointId}/achievements/pending")
    public Response getPendingAchievementsForCheckpoint(@PathParam("checkpointId") UUID checkpointId) {
        List<Achievement> pending = achievementService.findPendingByCheckpointId(checkpointId);
        return Response.ok(pending.stream().map(this::toDto).toList()).build();
    }

    /**
     * Game master verifies a pending achievement.
     * This awards points to the team.
     */
    @POST
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.CHECKPOINT_MANAGER})
    @Path("/achievements/{achievementId}/verify")
    public Response verifyAchievement(
            @PathParam("achievementId") UUID achievementId,
            VerificationRequest request,
            @Context SecurityContext securityContext) {
        UUID gameMasterId = securityContext.getUserPrincipal() != null ? UUID.fromString(securityContext.getUserPrincipal().getName()) : null;
        String notes = request != null ? request.notes() : null;
        int bonusPoints = request != null && request.bonusPoints() != null ? request.bonusPoints() : 0;

        return achievementService.verifyAchievement(achievementId, gameMasterId, notes, bonusPoints)
            .map(achievement -> Response.ok(toDto(achievement)).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Achievement not found or already processed"))
                .build());
    }

    /**
     * Game master rejects a pending achievement.
     * No points are awarded.
     */
    @POST
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.CHECKPOINT_MANAGER})
    @Path("/achievements/{achievementId}/reject")
    public Response rejectAchievement(
            @PathParam("achievementId") UUID achievementId,
            VerificationRequest request,
            @Context SecurityContext securityContext) {
        UUID gameMasterId = securityContext.getUserPrincipal() != null ? UUID.fromString(securityContext.getUserPrincipal().getName()) : null;
        String notes = request != null ? request.notes() : null;

        if (notes == null || notes.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("Rejection reason (notes) is required"))
                .build();
        }

        return achievementService.rejectAchievement(achievementId, gameMasterId, notes)
            .map(achievement -> Response.ok(toDto(achievement)).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Achievement not found or already processed"))
                .build());
    }

    /**
     * Adjust bonus points on an already-VERIFIED achievement. Used after the
     * tracker has auto-verified a checkpoint pass and personnel want to add
     * (or correct) bonus points on the spot. The delta is recorded in the
     * points ledger as a MANUAL_ADJUSTMENT.
     */
    @POST
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.CHECKPOINT_MANAGER})
    @Path("/achievements/{achievementId}/bonus-points")
    public Response adjustBonusPoints(
            @PathParam("achievementId") UUID achievementId,
            BonusPointsAdjustmentRequest request,
            @Context SecurityContext securityContext) {
        if (request == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("Request body is required"))
                .build();
        }
        UUID gameMasterId = securityContext.getUserPrincipal() != null
            ? UUID.fromString(securityContext.getUserPrincipal().getName()) : null;

        return achievementService.addBonusPoints(achievementId, request.bonusDelta(), gameMasterId)
            .map(achievement -> Response.ok(toDto(achievement)).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Achievement not found or not VERIFIED"))
                .build());
    }

    // ==================== Checkpoint Station Endpoints ====================

    /**
     * Create a PENDING achievement for a team at a checkpoint.
     * Used from the checkpoint station UI when a team did not auto-detect via location
     * polling, so verify/reject can proceed. Notifies game masters.
     */
    @POST
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.CHECKPOINT_MANAGER})
    @Path("/achievements/detect")
    public Response detectProximity(ProximityDetectionRequest request) {
        if (request.teamId() == null || request.checkpointId() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("teamId and checkpointId are required"))
                .build();
        }

        try {
            return achievementService.detectProximity(request.teamId(), request.checkpointId())
                .map(achievement -> Response.status(Response.Status.CREATED)
                    .entity(toDto(achievement))
                    .build())
                .orElse(Response.status(Response.Status.CONFLICT)
                    .entity(new ErrorResponse("Achievement already detected or verified for this checkpoint"))
                    .build());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        }
    }

    // ==================== Helper Methods ====================

    private AchievementResponse toDto(Achievement achievement) {
        return new AchievementResponse(
            achievement.getId(),
            achievement.getTeamId(),
            achievement.getCheckpointId(),
            achievementService.computePointsEarned(achievement),
            achievement.getBonusPoints(),
            achievement.getStatus().name(),
            achievement.getDetectedAt() != null ? achievement.getDetectedAt().toString() : null,
            achievement.getVerifiedAt() != null ? achievement.getVerifiedAt().toString() : null,
            achievement.getNotes()
        );
    }
}
