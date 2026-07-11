package nl.vlettenrally.api.v1;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import nl.vlettenrally.data.dto.auth.AuthTokenResponse;
import nl.vlettenrally.data.dto.auth.CheckInStateResponse;
import nl.vlettenrally.data.dto.auth.RegisterDeviceRequest;
import nl.vlettenrally.data.dto.auth.RegisterDeviceResponse;
import nl.vlettenrally.data.dto.auth.TeamInvite;
import nl.vlettenrally.data.dto.auth.TeamInviteLinkResponse;
import nl.vlettenrally.data.dto.auth.TeamInviteResolveResponse;
import nl.vlettenrally.data.dto.auth.TeamOptionResponse;
import nl.vlettenrally.data.dto.auth.TeamSelectionRequest;
import nl.vlettenrally.data.dto.auth.TeamSelectionResponse;
import nl.vlettenrally.data.dto.auth.TodayGameResponse;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.service.AuthService;
import nl.vlettenrally.service.CheckInCacheService;
import nl.vlettenrally.service.CheckInCoordinationService;
import nl.vlettenrally.service.CheckInWindowClosedException;
import nl.vlettenrally.service.TeamNotCheckedInException;
import nl.vlettenrally.service.TeamService;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * REST Resource for Check-in operations.
 * <p>
 * Public (no auth) endpoints power the self-service flow:
 * - GET  /api/v1/check-in/games/today         - games eligible for check-in (start today, or checkInStartTime reached)
 * - GET  /api/v1/check-in/games/{gameId}/teams - team list for a game
 * - POST /api/v1/check-in/register-device      - claim a device + issue team tokens
 * <p>
 * Team-authenticated endpoints:
 * - GET  /api/v1/check-in/state                - coordination state (used by waiting devices)
 * <p>
 * Legacy registration-token endpoints (preserved for the WordPress check-in link):
 * - GET  /api/v1/check-in/teams                - team options for a registration-token holder
 * - POST /api/v1/check-in/exchange-token        - exchange registration token for team tokens
 */
@Path("/api/v1/check-in")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CheckInResource {

    @Inject
    AuthService authService;

    @Inject
    TeamService teamService;

    @Inject
    CheckInCoordinationService coordinationService;

    @Inject
    CheckInCacheService checkInCacheService;

    // ---------------------------------------------------------------------
    // Public self-service endpoints
    // ---------------------------------------------------------------------

    @GET
    @PermitAll
    @Path("/games/today")
    public Response getGamesToday() {
        List<TodayGameResponse> games = coordinationService.findCheckInGamesForToday().stream()
            .map(g -> new TodayGameResponse(g.getId(), g.getName(), g.getDescription(), g.getStartTime()))
            .toList();
        return Response.ok(games).build();
    }

    @GET
    @PermitAll
    @Path("/games/{gameId}/teams")
    public Response getTeamsForGame(@PathParam("gameId") UUID gameId) {
        List<TeamOptionResponse> teams = checkInCacheService.getOrLoad(gameId, () -> {
            Map<UUID, Boolean> acceptingByTeam = coordinationService.acceptingNewDevicesByTeam(gameId);
            return teamService.findByGameId(gameId).stream()
                .map(team -> new TeamOptionResponse(
                    team.getId(),
                    team.getName(),
                    team.getDescription(),
                    team.isCheckedIn(),
                    team.getMainDeviceId() != null,
                    acceptingByTeam.getOrDefault(team.getId(), true)))
                .toList();
        });
        return Response.ok(teams).build();
    }

    @POST
    @PermitAll
    @Path("/register-device")
    public Response registerDevice(RegisterDeviceRequest request) {
        if (request == null || request.gameId() == null || request.teamId() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("gameId and teamId are required"))
                .build();
        }
        try {
            RegisterDeviceResponse response = coordinationService.registerDevice(
                request.gameId(), request.teamId(), request.deviceId(), request.nickname(), request.inviteToken());
            return Response.ok(response).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        } catch (TeamNotCheckedInException e) {
            // Team hasn't been physically checked in at the rally desk yet. 409 (not 403)
            // for the same reason as below — the frontend treats 401/403 as an auth failure.
            return Response.status(Response.Status.CONFLICT)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        } catch (CheckInWindowClosedException e) {
            // Digital check-in time gate closed for the manual selection path. 409 (not
            // 403) is deliberate: this is a public endpoint and the frontend treats 401/403
            // as an auth failure that triggers a token refresh + login redirect.
            return Response.status(Response.Status.CONFLICT)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        } catch (IllegalStateException e) {
            // Per-team device cap reached.
            return Response.status(429)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        }
    }

    @GET
    @RolesAllowed({"team"})
    @Path("/state")
    public Response getState(@Context SecurityContext securityContext) {
        try {
            UUID teamId = UUID.fromString(securityContext.getUserPrincipal().getName());
            CheckInStateResponse state = coordinationService.getState(teamId);
            return Response.ok(state).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        }
    }

    /**
     * Mint a team-scoped invite token for the authenticated team. The team shows the
     * returned URL as a QR; teammates who scan it join this team directly and bypass
     * the digital check-in time gate.
     */
    @POST
    @RolesAllowed({"team"})
    @Path("/invite-token")
    public Response createInviteToken(@Context SecurityContext securityContext) {
        try {
            UUID teamId = UUID.fromString(securityContext.getUserPrincipal().getName());
            TeamInviteLinkResponse response = authService.generateTeamInviteToken(teamId);
            return Response.ok(response).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        }
    }

    /**
     * Resolve a scanned invite token to the game + team it grants access to, so the
     * public check-in flow can skip the game/team pickers and jump to the nickname step.
     */
    @GET
    @PermitAll
    @Path("/invite/{token}")
    public Response resolveInvite(@PathParam("token") String token) {
        Optional<TeamInvite> invite = authService.validateTeamInviteToken(token);
        if (invite.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("Invalid or expired invite link"))
                .build();
        }
        TeamInvite decoded = invite.get();
        Optional<Team> team = teamService.findById(decoded.teamId());
        if (team.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("Team no longer exists"))
                .build();
        }
        return Response.ok(new TeamInviteResolveResponse(
            decoded.gameId(),
            decoded.teamId(),
            team.get().getName())).build();
    }

    // ---------------------------------------------------------------------
    // Legacy WordPress registration-token endpoints
    // ---------------------------------------------------------------------

    /**
     * Get team options for check-in using a game-scoped check-in token.
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
                .map(team -> new TeamOptionResponse(
                    team.getId(),
                    team.getName(),
                    team.getDescription(),
                    team.isCheckedIn(),
                    team.getMainDeviceId() != null,
                    true))
                .toList();
            return Response.ok(new TeamSelectionResponse(gameId.toString(), teamOptions)).build();

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Failed to get team options: " + e.getMessage()))
                .build();
        }
    }

    /**
     * Exchange a check-in token for access + refresh tokens.
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
