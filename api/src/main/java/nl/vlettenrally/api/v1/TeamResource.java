package nl.vlettenrally.api.v1;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.websocket.server.PathParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import nl.vlettenrally.api.security.TeamScoped;
import nl.vlettenrally.api.security.TeamScopedOrAdmin;
import nl.vlettenrally.data.converter.TeamConverter;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.data.dto.team.EmergencyPhoneUpdateRequest;
import nl.vlettenrally.data.dto.team.ParticipantsUpdateRequest;
import nl.vlettenrally.data.dto.team.TeamCreateRequest;
import nl.vlettenrally.data.dto.team.TeamUpdateRequest;
import nl.vlettenrally.service.TeamService;
import nl.vlettenrally.websockets.WebSocketPublisher;
import io.quarkus.logging.Log;

import java.util.ArrayList;
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
@RolesAllowed("admin")
public class TeamResource {

    @Inject
    private TeamService teamService;

    @Inject
    private TeamConverter teamConverter;

    @Inject
    private WebSocketPublisher webSocketPublisher;

    /**
     * Get all teams
     */
    @GET
    @Path("/teams")
    public Response getAllTeams() {
        List<Team> teams = teamService.findAll();
        return Response.ok(teams.stream().map(teamConverter::toDto).toList()).build();
    }

    /**
     * Get team by ID
     */
    @GET
    @Path("/teams/{teamId}")
    public Response getTeamById(@PathParam("teamId") UUID teamId) {
        return teamService.findById(teamId)
            .map(team -> Response.ok(teamConverter.toDto(team)).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Team not found"))
                .build());
    }

    /**
     * Get rival teams for a team
     */
    @GET
    @RolesAllowed({"admin", "team"})
    @TeamScopedOrAdmin
    @Path("/teams/{teamId}/rivals")
    public Response getRivalTeams(@PathParam("teamId") UUID teamId) {
        List<Team> rivals = teamService.findRivalTeams(teamId);
        return Response.ok(rivals.stream().map(teamConverter::toPublicResponse).toList()).build();
    }

    /**
     * Get current authenticated team's data
     * Requires authentication via access token
     */
    @GET
    @Path("/team/me")
    @RolesAllowed({ "team" })
    public Response getMyTeam(@Context SecurityContext securityContext) {
        UUID teamId = UUID.fromString(securityContext.getUserPrincipal().getName());

        return teamService.findById(teamId)
            .map(team -> Response.ok(teamConverter.toPlayerResponse(team)).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Team not found"))
                .build());
    }

    /**
     * Check in a team
     */
    @POST
    @RolesAllowed({"team"})
    @TeamScoped
    @Path("/teams/{teamId}/check-in")
    public Response checkInTeam(
            @PathParam("teamId") UUID teamId) {

        try {
            Team team = teamService.checkIn(teamId);
            return Response.ok(teamConverter.toDto(team)).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
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
    @RolesAllowed({"team"})
    @TeamScoped
    public Response updateEmergencyPhoneNumbers(
            @PathParam("teamId") UUID teamId,
            EmergencyPhoneUpdateRequest request) {

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
            
            return Response.ok(teamConverter.toDto(updatedTeam)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        }
    }

    /**
     * Update participants for a team
     */
    @PUT
    @Path("/teams/{teamId}/participants")
    @RolesAllowed({"team"})
    @TeamScoped
    public Response updateParticipants(
            @PathParam("teamId") UUID teamId,
            ParticipantsUpdateRequest request) {

        try {
            Optional<Team> teamOpt = teamService.findById(teamId);
            if (teamOpt.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Team not found"))
                    .build();
            }

            Team team = teamOpt.get();
            team.setParticipants(request.participants());
            Team updatedTeam = teamService.save(team);

            return Response.ok(teamConverter.toDto(updatedTeam)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        }
    }

    /**
     * Update an existing team
     */
    @PUT
    @Path("/teams/{teamId}")
    public Response updateTeam(
            @PathParam("teamId") UUID teamId,
            TeamUpdateRequest request) {
        
        try {
            Optional<Team> teamOpt = teamService.findById(teamId);
            if (teamOpt.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Team not found"))
                    .build();
            }

            Team team = teamOpt.get();
            
            // Update team fields
            if (request.name() != null) {
                team.setName(request.name());
            }
            if (request.description() != null) {
                team.setDescription(request.description());
            }
            if (request.participants() != null) {
                team.setParticipants(request.participants());
            }
            if (request.emergencyPhoneNumbers() != null) {
                team.setEmergencyPhoneNumbers(request.emergencyPhoneNumbers());
            }
            if (request.notes() != null) {
                team.setNotes(request.notes());
            }

            // Handle totalPoints update with non-negative validation
            if (request.totalPoints() != null) {
                if (request.totalPoints() < 0) {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Points cannot be negative"))
                        .build();
                }
                int oldPoints = team.getTotalPoints();
                team.setTotalPoints(request.totalPoints());
                Log.infof("Admin updated points for team %s (%s): %d → %d",
                    teamId, team.getName(), oldPoints, request.totalPoints());
            }

            // Handle check-in status updates with reset logic
            // If checkedIn is explicitly set to false, also reset checkedInDigitally
            if (request.checkedIn() != null) {
                team.setCheckedIn(request.checkedIn());
                if (!request.checkedIn()) {
                    // Reset digital check-in when unchecking
                    team.setCheckedInDigitally(false);
                }
            }

            // Allow explicit setting of checkedInDigitally (admin override)
            // Only if checkedIn wasn't just set to false
            if (request.checkedInDigitally() != null &&
                (request.checkedIn() == null || request.checkedIn())) {
                team.setCheckedInDigitally(request.checkedInDigitally());
            }

            Team updatedTeam = teamService.save(team);
            
            // Broadcast update via WebSocket
            webSocketPublisher.broadcastToGame(
                updatedTeam.getGameId(), 
                "team:updated", 
                teamConverter.toDto(updatedTeam)
            );
            
            return Response.ok(teamConverter.toDto(updatedTeam)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        }
    }

    /**
     * Create a new team
     */
    @POST
    @Path("/teams")
    public Response createTeam(TeamCreateRequest request) {
        try {
            // Validate required fields
            if (request.gameId() == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Game ID is required"))
                    .build();
            }
            if (request.name() == null || request.name().trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Team name is required"))
                    .build();
            }

            Team team = new Team();
            team.setGameId(request.gameId());
            team.setName(request.name());
            team.setDescription(request.description() != null ? request.description() : "");
            team.setParticipants(request.participants() != null ? request.participants() : new ArrayList<>());
            team.setEmergencyPhoneNumbers(request.emergencyPhoneNumbers() != null ? request.emergencyPhoneNumbers() : new ArrayList<>());
            team.setNotes(request.notes());

            Team savedTeam = teamService.save(team);
            
            // Broadcast creation via WebSocket
            webSocketPublisher.broadcastToGame(
                savedTeam.getGameId(), 
                "team:created", 
                teamConverter.toDto(savedTeam)
            );
            
            return Response.status(Response.Status.CREATED)
                .entity(teamConverter.toDto(savedTeam))
                .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        }
    }
}