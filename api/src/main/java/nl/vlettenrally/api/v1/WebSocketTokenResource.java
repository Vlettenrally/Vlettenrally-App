package nl.vlettenrally.api.v1;

import java.util.UUID;

import io.quarkus.logging.Log;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.vlettenrally.api.security.SecurityRoles;
import nl.vlettenrally.data.dto.auth.WebSocketTokenResponse;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.websockets.WebSocketTokenResult;
import nl.vlettenrally.websockets.WebSocketTokenService;

/**
 * REST Resource for obtaining WebSocket connection tokens.
 * <p>
 * These short-lived tokens are used to authenticate WebSocket connections
 * without relying on annotations that can interfere with session lifecycle.
 * <p>
 * Endpoints:
 * - GET /api/v1/ws/token/admin - Get token for admin WebSocket connection
 * - GET /api/v1/ws/token/team/{gameId}/{teamId} - Get token for team WebSocket connection
 */
@Path("/api/v1/ws/token")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WebSocketTokenResource {

    @Inject
    WebSocketTokenService tokenService;

    @Inject
    SecurityIdentity securityIdentity;


    /**
     * Generate a WebSocket token for admin connections.
     * Requires admin role.
     */
    @GET
    @Path("/admin")
    @RolesAllowed(SecurityRoles.STAFF)
    public Uni<Response> getAdminToken() {
        String userId = securityIdentity.getPrincipal().getName();
        String userName = securityIdentity.getAttribute("name");

        if (userName == null) {
            userName = "Admin";
        }

        Log.infof("Generating WebSocket token for admin: %s (%s)", userName, userId);

        return tokenService.generateAdminToken(UUID.fromString(userId), userName)
            .map(token -> Response.ok(new WebSocketTokenResponse(token, 30)).build())
            .onFailure().recoverWithItem(error -> {
                Log.errorf("Failed to generate admin WebSocket token: %s", error.getMessage());
                return Response.serverError()
                    .entity(new ErrorResponse("Failed to generate WebSocket token"))
                    .build();
            });
    }

    /**
     * Generate a WebSocket token for team connections.
     * Requires team role, and the team must match the authenticated user.
     */
    @GET
    @Path("/team/{gameId}/{teamId}")
    @RolesAllowed({SecurityRoles.TEAM, SecurityRoles.STAFF})
    public Uni<Response> getTeamToken(
            @PathParam("gameId") String gameIdStr,
            @PathParam("teamId") String teamIdStr) {

        UUID gameId;
        UUID teamId;
        try {
            gameId = UUID.fromString(gameIdStr);
            teamId = UUID.fromString(teamIdStr);
        } catch (IllegalArgumentException e) {
            return Uni.createFrom().item(
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Invalid gameId or teamId format"))
                    .build()
            );
        }

        String userId = securityIdentity.getPrincipal().getName();
        boolean isAdmin = securityIdentity.hasRole(SecurityRoles.STAFF);

        // Check authorization: admins can get tokens for any team, teams only for themselves
        if (!isAdmin && !userId.equals(teamIdStr)) {
            Log.warnf("Team %s attempted to get WebSocket token for team %s", userId, teamIdStr);
            return Uni.createFrom().item(
                Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse("Cannot get token for another team"))
                    .build()
            );
        }

        Log.infof("Generating WebSocket token for team: %s (game: %s)", teamId, gameId);

        return tokenService.generateTeamToken(teamId, gameId)
            .map(result -> switch (result) {
                case WebSocketTokenResult.Success success ->
                    Response.ok(new WebSocketTokenResponse(success.token(), success.expiresInSeconds())).build();
                case WebSocketTokenResult.TeamNotFound ignored ->
                    Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("Team not found"))
                        .build();
                case WebSocketTokenResult.TeamGameMismatch ignored ->
                    Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Team does not belong to the specified game"))
                        .build();
                case WebSocketTokenResult.Error error ->
                    Response.serverError()
                        .entity(new ErrorResponse(error.message()))
                        .build();
            })
            .onFailure().recoverWithItem(error -> {
                Log.errorf("Failed to generate team WebSocket token: %s", error.getMessage());
                return Response.serverError()
                    .entity(new ErrorResponse("Failed to generate WebSocket token"))
                    .build();
            });
    }
}


