package nl.vlettenrally.api.v1;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import nl.vlettenrally.api.security.Secured;
import nl.vlettenrally.data.converter.TeamConverter;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.data.dto.team.EmergencyPhoneUpdateRequest;
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
@Secured
@RolesAllowed("admin")
public class TeamResource {

    @Inject
    private TeamService teamService;

    @Inject
    private TeamConverter teamConverter;

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
    @Path("/teams/{teamId}/rivals")
    public Response getRivalTeams(@PathParam("teamId") UUID teamId) {
        List<Team> rivals = teamService.findRivalTeams(teamId);
        return Response.ok(rivals.stream().map(teamConverter::toDto).toList()).build();
    }

    /**
     * Get current authenticated team's data
     * Requires authentication via access token
     */
    @GET
    @Path("/team/me")
    @RolesAllowed({"team"})
    @Secured
    public Response getMyTeam(@Context SecurityContext securityContext) {
        UUID teamId = getAuthenticatedTeamId(securityContext);

        return teamService.findById(teamId)
            .map(team -> Response.ok(teamConverter.toDto(team)).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Team not found"))
                .build());
    }

    /**
     * Check in a team
     */
    @POST
    @RolesAllowed({"team"})
    @Path("/teams/{teamId}/check-in")
    @Secured
    public Response checkInTeam(
            @PathParam("teamId") UUID teamId,
            @Context SecurityContext securityContext) {
        
        UUID authTeamId = getAuthenticatedTeamId(securityContext);

        // Verify the authenticated team matches the requested team
        if (!authTeamId.equals(teamId)) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ErrorResponse("Cannot check in another team"))
                .build();
        }

        try {
            Team team = teamService.checkIn(teamId);
            return Response.ok(teamConverter.toDto(team)).build();
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
    @Secured
    public Response updateEmergencyPhoneNumbers(
            @PathParam("teamId") UUID teamId,
            @Context SecurityContext securityContext,
            EmergencyPhoneUpdateRequest request) {
        
        UUID authTeamId = getAuthenticatedTeamId(securityContext);

        // Verify the authenticated team matches the requested team
        if (!authTeamId.equals(teamId)) {
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
            
            return Response.ok(teamConverter.toDto(updatedTeam)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        }
    }

    // Helper methods

    private UUID getAuthenticatedTeamId(SecurityContext securityContext) {
        return UUID.fromString(securityContext.getUserPrincipal().getName());
    }
}