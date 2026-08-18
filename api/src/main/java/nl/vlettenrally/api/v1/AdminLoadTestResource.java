package nl.vlettenrally.api.v1;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.vlettenrally.api.security.SecurityRoles;
import nl.vlettenrally.data.dto.admin.LoadTestScenarioResponse;
import nl.vlettenrally.data.dto.admin.LoadTestSeedRequest;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.service.LoadTestSeedService;

import java.util.UUID;

/**
 * Super-admin-only endpoints to provision and clean up a disposable load-test game.
 * <p>
 * - POST /api/v1/admin/load-test/seed        — create a marked game with N teams + M checkpoints
 * - DELETE /api/v1/admin/load-test/{gameId}  — delete a marked game and its S3 picture objects
 * <p>
 * Restricted to {@link SecurityRoles#SUPER_ADMIN} because it creates and destroys whole games.
 */
@Path("/api/v1/admin/load-test")
@RolesAllowed(SecurityRoles.SUPER_ADMIN)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminLoadTestResource {

    private static final int DEFAULT_TEAM_COUNT = 250;
    private static final int DEFAULT_CHECKPOINT_COUNT = 12;
    private static final double DEFAULT_CENTER_LAT = 52.35;
    private static final double DEFAULT_CENTER_LNG = 4.90;
    private static final double DEFAULT_SPREAD_KM = 3.0;
    private static final int DEFAULT_START_IN_MINUTES = 7; // pre-start window, within the asked 5–10 min
    private static final String DEFAULT_ENDPOINT_URL = "http://loadtest-location:8080/api/event/1";

    @Inject
    LoadTestSeedService seedService;

    @POST
    @Path("/seed")
    public Response seed(LoadTestSeedRequest request) {
        int teamCount = value(request == null ? null : request.teamCount(), DEFAULT_TEAM_COUNT);
        int checkpointCount = value(request == null ? null : request.checkpointCount(), DEFAULT_CHECKPOINT_COUNT);
        double centerLat = value(request == null ? null : request.centerLat(), DEFAULT_CENTER_LAT);
        double centerLng = value(request == null ? null : request.centerLng(), DEFAULT_CENTER_LNG);
        double spreadKm = value(request == null ? null : request.spreadKm(), DEFAULT_SPREAD_KM);
        String url = (request != null && request.locationEndpointUrl() != null && !request.locationEndpointUrl().isBlank())
            ? request.locationEndpointUrl().trim()
            : DEFAULT_ENDPOINT_URL;
        boolean seedAutomations = request == null || request.seedAutomations() == null || request.seedAutomations();
        int startInMinutes = value(request == null ? null : request.startInMinutes(), DEFAULT_START_IN_MINUTES);
        // Default false: the k6 driver plays the rally desk and the app, so check-in is part
        // of the load test rather than something the seed quietly hands out.
        boolean preCheckIn = request != null && Boolean.TRUE.equals(request.preCheckIn());

        if (teamCount < 1 || teamCount > 2000) {
            return badRequest("teamCount must be between 1 and 2000");
        }
        if (checkpointCount < 2 || checkpointCount > 200) {
            return badRequest("checkpointCount must be between 2 and 200");
        }
        if (spreadKm <= 0 || spreadKm > 200) {
            return badRequest("spreadKm must be between 0 (exclusive) and 200");
        }
        if (startInMinutes < 0 || startInMinutes > 1440) {
            return badRequest("startInMinutes must be between 0 and 1440");
        }

        LoadTestScenarioResponse scenario =
            seedService.seed(teamCount, checkpointCount, centerLat, centerLng, spreadKm, url, seedAutomations, startInMinutes, preCheckIn);
        return Response.ok(scenario).build();
    }

    @DELETE
    @Path("/{gameId}")
    public Response teardown(@PathParam("gameId") UUID gameId) {
        try {
            seedService.teardown(gameId);
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(new ErrorResponse(e.getMessage())).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(new ErrorResponse(e.getMessage())).build();
        }
    }

    private static int value(Integer v, int dflt) {
        return v != null ? v : dflt;
    }

    private static double value(Double v, double dflt) {
        return v != null ? v : dflt;
    }

    private static Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST).entity(new ErrorResponse(message)).build();
    }
}
