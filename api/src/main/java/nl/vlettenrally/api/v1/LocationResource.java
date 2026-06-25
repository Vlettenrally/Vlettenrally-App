package nl.vlettenrally.api.v1;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.vlettenrally.api.security.SecurityRoles;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.service.LocationService;

import java.util.UUID;

/**
 * REST Resource for Location data operations.
 * <p>
 * Endpoints:
 * - GET /api/v1/games/{gameId}/locations/latest - Get latest location per team (admin-only)
 * - GET /api/v1/games/{gameId}/locations/team/{teamId}/history - Get a team's recent location history (admin-only)
 */
@Path("/api/v1/games/{gameId}/locations")
@RolesAllowed(SecurityRoles.STAFF)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LocationResource {

    @Inject
    LocationService locationService;

    @GET
    @Path("/latest")
    public Response getLatestTeamLocations(@PathParam("gameId") UUID gameId) {
        return locationService.getLatestTeamLocations(gameId)
            .map(locations -> Response.ok(locations).build())
            .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Game not found"))
                .build());
    }

    @GET
    @Path("/team/{teamId}/history")
    public Response getTeamLocationHistory(
        @PathParam("gameId") UUID gameId,
        @PathParam("teamId") UUID teamId,
        @QueryParam("windowMinutes") @DefaultValue("60") int windowMinutes
    ) {
        return locationService.getTeamLocationHistory(gameId, teamId, windowMinutes)
            .map(points -> Response.ok(points).build())
            .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Game or team not found"))
                .build());
    }
}
