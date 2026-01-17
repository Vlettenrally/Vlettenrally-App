package nl.vlettenrally.api.v1;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.vlettenrally.domain.Team;
import nl.vlettenrally.service.AuthService;
import nl.vlettenrally.service.TeamService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * REST Resource for Team operations
 * 
 * Endpoints:
 * - GET /api/v1/teams - Get all teams
 * - GET /api/v1/teams/{teamId} - Get team by ID
 * - GET /api/v1/teams/{teamId}/rival - Get rival team
 * - GET /api/v1/team/me - Get current authenticated team
 * - POST /api/v1/teams/{teamId}/check-in - Check in a team
 * - PUT /api/v1/teams/{teamId}/emergency-phones - Update emergency phone numbers
 */
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TeamResource {

    @Inject
    TeamService teamService;

    @Inject
    AuthService authService;

    /**
     * Get all teams
     */
    @GET
    @Path("/teams")
    public Response getAllTeams() {
        List<Team> teams = teamService.findAll();
        return Response.ok(teams.stream().map(this::toDto).toList()).build();
    }

    /**
     * Get team by ID
     */
    @GET
    @Path("/teams/{teamId}")
    public Response getTeamById(@PathParam("teamId") UUID teamId) {
        return teamService.findById(teamId)
            .map(team -> Response.ok(toDto(team)).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Team not found"))
                .build());
    }

    /**
     * Get rival team for a team
     */
    @GET
    @Path("/teams/{teamId}/rival")
    public Response getRivalTeam(@PathParam("teamId") UUID teamId) {
        return teamService.findRivalTeam(teamId)
            .map(team -> Response.ok(toDto(team)).build())
            .orElse(Response.ok().build()); // Return empty if no rival
    }

    /**
     * Get current authenticated team's data
     * Requires authentication via access token
     */
    @GET
    @Path("/team/me")
    public Response getMyTeam(@HeaderParam("Authorization") String authHeader) {
        String token = extractToken(authHeader);

        if (token == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse("Missing or invalid authorization token"))
                .build();
        }

        Optional<UUID> teamIdOpt = authService.validateAccessToken(token);

        if (teamIdOpt.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse("Invalid or expired access token"))
                .build();
        }

        return teamService.findById(teamIdOpt.get())
            .map(team -> Response.ok(toDto(team)).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Team not found"))
                .build());
    }

    /**
     * Check in a team
     */
    @POST
    @Path("/teams/{teamId}/check-in")
    public Response checkInTeam(
            @PathParam("teamId") UUID teamId,
            @HeaderParam("Authorization") String authHeader) {
        
        String token = extractToken(authHeader);
        if (token == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse("Missing or invalid authorization token"))
                .build();
        }

        Optional<UUID> authTeamId = authService.validateAccessToken(token);
        if (authTeamId.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse("Invalid or expired access token"))
                .build();
        }

        // Verify the authenticated team matches the requested team
        if (!authTeamId.get().equals(teamId)) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ErrorResponse("Cannot check in another team"))
                .build();
        }

        try {
            Team team = teamService.checkIn(teamId);
            return Response.ok(toDto(team)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        }
    }

    /**
     * Update emergency phone numbers for a team
     */
    @PUT
    @Path("/teams/{teamId}/emergency-phones")
    public Response updateEmergencyPhoneNumbers(
            @PathParam("teamId") UUID teamId,
            @HeaderParam("Authorization") String authHeader,
            EmergencyPhoneUpdateRequest request) {
        
        String token = extractToken(authHeader);
        if (token == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse("Missing or invalid authorization token"))
                .build();
        }

        Optional<UUID> authTeamId = authService.validateAccessToken(token);
        if (authTeamId.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse("Invalid or expired access token"))
                .build();
        }

        // Verify the authenticated team matches the requested team
        if (!authTeamId.get().equals(teamId)) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ErrorResponse("Cannot update another team's phone numbers"))
                .build();
        }

        try {
            Optional<Team> teamOpt = teamService.findById(teamId);
            if (teamOpt.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Team not found"))
                    .build();
            }

            Team team = teamOpt.get();
            team.setEmergencyPhoneNumbers(request.phoneNumbers());
            Team updatedTeam = teamService.save(team);
            
            return Response.ok(toDto(updatedTeam)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        }
    }

    // Helper methods

    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    private TeamDto toDto(Team team) {
        return new TeamDto(
            team.getId(),
            team.getGameId(),
            team.getName(),
            team.getDescription(),
            team.getBoatNumber(),
            team.getColor(),
            team.getParticipants(),
            team.getDeviceId(),
            team.isCheckedIn(),
            team.getPoints(),
            team.getRivals(),
            team.getNotes(),
            team.getEmergencyPhoneNumbers(),
            team.getCreatedAt() != null ? team.getCreatedAt().toString() : null,
            team.getUpdatedAt() != null ? team.getUpdatedAt().toString() : null
        );
    }

    // DTOs
    public record TeamDto(
        UUID id,
        UUID game,
        String name,
        String description,
        String boatNumber,
        String color,
        List<String> participants,
        UUID deviceId,
        boolean checkedIn,
        int points,
        List<UUID> rivals,
        String notes,
        List<String> emergencyPhoneNumbers,
        String createdAt,
        String updatedAt
    ) {}

    public record ErrorResponse(String message) {}

    public record EmergencyPhoneUpdateRequest(List<String> phoneNumbers) {}
}