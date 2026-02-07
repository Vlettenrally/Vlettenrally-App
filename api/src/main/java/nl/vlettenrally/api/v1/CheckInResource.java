package nl.vlettenrally.api.v1;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import nl.vlettenrally.data.dto.auth.AuthTokenResponse;
import nl.vlettenrally.data.dto.auth.TeamOptionResponse;
import nl.vlettenrally.data.dto.auth.TeamSelectionRequest;
import nl.vlettenrally.data.dto.auth.TeamSelectionResponse;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.service.AuthService;
import nl.vlettenrally.service.TeamService;

import java.util.List;
import java.util.UUID;

/**
 * REST Resource for Check-in operations
 * 
 * Endpoints:
 * - GET /api/v1/check-in/teams - Get team options for check-in (requires check-in token)
 * - POST /api/v1/check-in/exchange-token - Exchange check-in token for access tokens (with team selection)
 */
@Path("/api/v1/check-in")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CheckInResource {

    @Inject
    AuthService authService;

    @Inject
    TeamService teamService;

    /**
     * Get team options for check-in using a game-scoped check-in token
     * Returns a list of teams that can be selected for check-in
     */
    @GET
    @RolesAllowed({"checkin"})
    @Path("/teams")
    public Response getTeamOptions(@Context SecurityContext securityContext) {
        try {
            UUID gameId = UUID.fromString(securityContext.getUserPrincipal().getName());

            if (!authService.isCheckInTokenValid(gameId)) {
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ErrorResponse("Invalid or expired check-in token"))
                    .build();
            }

            List<TeamOptionResponse> teamOptions = teamService.findByGameId(gameId).stream()
                .map(team -> new TeamOptionResponse(team.getId(), team.getName(), team.getBoatNumber()))
                .toList();
            return Response.ok(new TeamSelectionResponse(gameId.toString(), teamOptions)).build();

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Failed to get team options: " + e.getMessage()))
                .build();
        }
    }

    /**
     * Exchange a check-in token for access + refresh tokens
     * Requires selecting a team. Only succeeds if team hasn't already checked in.
     */
    @POST
    @RolesAllowed({"checkin"})
    @Path("/exchange-token")
    public Response exchangeToken(TeamSelectionRequest request, @Context SecurityContext securityContext) {
        try {
            if (request.teamId() == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("teamId is required"))
                    .build();
            }

            UUID gameId = UUID.fromString(securityContext.getUserPrincipal().getName());

            return authService.selectTeam(gameId, request.teamId())
                .map(result -> Response.ok(new AuthTokenResponse(
                    result.accessToken(),
                    result.refreshToken(),
                    result.expiresIn()
                )).build())
                .orElse(Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ErrorResponse("Invalid or expired check-in token"))
                    .build());

        } catch (IllegalStateException e) {
            // Team is already checked in (matches validation in TeamServiceImpl.checkIn())
            return Response.status(Response.Status.CONFLICT)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Team selection failed: " + e.getMessage()))
                .build();
        }
    }
}
