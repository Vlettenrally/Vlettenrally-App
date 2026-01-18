package nl.vlettenrally.api.v1;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.vlettenrally.data.domain.Achievement;
import nl.vlettenrally.data.dto.achievement.AchievementResponse;
import nl.vlettenrally.data.dto.achievement.ProximityDetectionRequest;
import nl.vlettenrally.data.dto.achievement.VerificationRequest;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.service.AchievementService;
import nl.vlettenrally.service.AuthService;

import java.util.List;
import java.util.UUID;

/**
 * REST Resource for Achievement operations.
 * 
 * Achievement Flow:
 * 1. System detects team proximity to checkpoint → POST /api/v1/achievements/detect (internal/system)
 * 2. Game master sees pending achievements → GET /api/v1/checkpoints/{id}/achievements/pending
 * 3. Game master verifies → POST /api/v1/achievements/{id}/verify
 * 4. Game master rejects → POST /api/v1/achievements/{id}/reject
 * 
 * Teams can only view their own achievements (read-only).
 */
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AchievementResource {

    @Inject
    AchievementService achievementService;

    @Inject
    AuthService authService;

    // ==================== Team Endpoints (Read-only) ====================

    /**
     * Get all achievements for a team (teams can view their own)
     */
    @GET
    @Path("/teams/{teamId}/achievements")
    public Response getTeamAchievements(@PathParam("teamId") UUID teamId) {
        List<Achievement> achievements = achievementService.findByTeamId(teamId);
        return Response.ok(achievements.stream().map(this::toDto).toList()).build();
    }

    /**
     * Get verified achievements for a team (only completed ones with points)
     */
    @GET
    @Path("/teams/{teamId}/achievements/verified")
    public Response getTeamVerifiedAchievements(@PathParam("teamId") UUID teamId) {
        List<Achievement> achievements = achievementService.findByTeamId(teamId).stream()
            .filter(a -> a.getStatus() == Achievement.Status.VERIFIED)
            .toList();
        return Response.ok(achievements.stream().map(this::toDto).toList()).build();
    }

    // ==================== Game Master Endpoints ====================

    /**
     * Get all pending achievements (for game master dashboard)
     */
    @GET
    @Path("/achievements/pending")
    public Response getAllPendingAchievements() {
        // TODO: Add game master authentication
        List<Achievement> pending = achievementService.findAllPending();
        return Response.ok(pending.stream().map(this::toDto).toList()).build();
    }

    /**
     * Get pending achievements for a specific checkpoint (for game masters at that location)
     */
    @GET
    @Path("/checkpoints/{checkpointId}/achievements/pending")
    public Response getPendingAchievementsForCheckpoint(@PathParam("checkpointId") UUID checkpointId) {
        // TODO: Add game master authentication
        List<Achievement> pending = achievementService.findPendingByCheckpointId(checkpointId);
        return Response.ok(pending.stream().map(this::toDto).toList()).build();
    }

    /**
     * Game master verifies a pending achievement.
     * This awards points to the team.
     */
    @POST
    @Path("/achievements/{achievementId}/verify")
    public Response verifyAchievement(
            @PathParam("achievementId") UUID achievementId,
            VerificationRequest request) {
        // TODO: Add game master authentication and get gameMasterId from token
        UUID gameMasterId = null; // Will come from auth later
        String notes = request != null ? request.notes() : null;

        return achievementService.verifyAchievement(achievementId, gameMasterId, notes)
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
    @Path("/achievements/{achievementId}/reject")
    public Response rejectAchievement(
            @PathParam("achievementId") UUID achievementId,
            VerificationRequest request) {
        // TODO: Add game master authentication and get gameMasterId from token
        UUID gameMasterId = null; // Will come from auth later
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

    // ==================== System/Internal Endpoints ====================

    /**
     * System endpoint: Detect team proximity to checkpoint.
     * Called by the location tracking system when a team is detected near a checkpoint.
     * Creates a PENDING achievement and should trigger notification to game masters.
     * 
     * NOTE: This should be secured for internal system use only.
     */
    @POST
    @Path("/achievements/detect")
    public Response detectProximity(ProximityDetectionRequest request) {
        // TODO: Add internal API key or system authentication
        
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
            achievement.getPointsEarned(),
            achievement.getStatus().name(),
            achievement.getDetectedAt() != null ? achievement.getDetectedAt().toString() : null,
            achievement.getVerifiedAt() != null ? achievement.getVerifiedAt().toString() : null,
            achievement.getNotes()
        );
    }
}
