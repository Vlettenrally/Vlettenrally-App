package nl.vlettenrally.api.v1;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.vlettenrally.api.security.SecurityRoles;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.data.dto.tracker.AddTrackersRequest;
import nl.vlettenrally.data.dto.tracker.AddTrackersResponse;
import nl.vlettenrally.data.dto.tracker.DeleteTrackersRequest;
import nl.vlettenrally.data.dto.tracker.DeleteTrackersResponse;
import nl.vlettenrally.data.dto.tracker.SetTrackerEnabledRequest;
import nl.vlettenrally.data.dto.tracker.TrackerAssignmentResponse;
import nl.vlettenrally.data.dto.tracker.TrackerResponse;
import nl.vlettenrally.service.TrackerService;

import java.util.List;

/**
 * REST Resource for the global tracker pool: bulk-adding tracker IDs, listing
 * them with health status (last seen, battery, last position) and current
 * assignment, and enabling/disabling a tracker so it is skipped when assigning.
 * <p>
 * Rally-desk territory: restricted to admin and rally_desk.
 */
@Path("/api/v1/trackers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TrackerResource {

    @Inject
    TrackerService trackerService;

    /**
     * All trackers in the global pool with status and current assignment.
     */
    @GET
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.RALLY_DESK})
    public Response listTrackers() {
        List<TrackerResponse> trackers = trackerService.listTrackers().stream()
                .map(TrackerResource::toResponse)
                .toList();
        return Response.ok(trackers).build();
    }

    /**
     * Bulk-add a pasted batch of tracker IDs to the global pool. Idempotent:
     * IDs already in the pool are reported as duplicates.
     */
    @POST
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.RALLY_DESK})
    public Response addTrackers(AddTrackersRequest request) {
        TrackerService.AddResult result = trackerService.addTrackers(
                request != null ? request.trackerIds() : List.of());
        return Response.ok(new AddTrackersResponse(result.added(), result.duplicates())).build();
    }

    /**
     * Bulk-remove tracker IDs from the global pool. Teams in a planned or active
     * game that hold one of the deleted trackers are unassigned first, so no team
     * is left pointing at a tracker that is no longer in the pool.
     * <p>
     * Modelled as a POST because it carries a body of tracker IDs, mirroring the
     * bulk-add above.
     */
    @POST
    @Path("/delete")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.RALLY_DESK})
    public Response deleteTrackers(DeleteTrackersRequest request) {
        if (request == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Request body is required")).build();
        }
        TrackerService.DeleteResult result = trackerService.deleteTrackers(request.trackerIds());
        return Response.ok(new DeleteTrackersResponse(
                result.deleted(), result.notFound(), result.unassignedTeams())).build();
    }

    /**
     * Enable or disable a tracker. Disabled trackers stay in the pool (and keep
     * receiving status updates) but are skipped when assigning trackers to teams.
     */
    @PUT
    @Path("/{trackerId}/enabled")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.RALLY_DESK})
    public Response setEnabled(@PathParam("trackerId") String trackerId, SetTrackerEnabledRequest request) {
        if (request == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Request body is required")).build();
        }
        return trackerService.setEnabled(trackerId, request.enabled())
                .map(tracker -> {
                    // The list decorates assignments; a single toggle doesn't need them.
                    TrackerService.TrackerWithAssignments updated =
                            new TrackerService.TrackerWithAssignments(tracker, List.of());
                    return Response.ok(toResponse(updated)).build();
                })
                .orElse(Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("Tracker not found")).build());
    }

    private static TrackerResponse toResponse(TrackerService.TrackerWithAssignments item) {
        return new TrackerResponse(
                item.tracker().getTrackerId(),
                item.tracker().isEnabled(),
                item.tracker().getBatteryPercentage(),
                item.tracker().getLastSeenAt(),
                item.tracker().getLastLatitude(),
                item.tracker().getLastLongitude(),
                item.assignments().stream()
                        .map(a -> new TrackerAssignmentResponse(a.teamName(), a.gameName()))
                        .toList()
        );
    }
}
