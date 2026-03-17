package nl.vlettenrally.api.v1;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.vlettenrally.data.domain.LocationData;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.data.dto.game.TeamLocationResponse;
import nl.vlettenrally.data.repository.LocationDataRepository;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.service.GameService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST Resource for Location data operations
 *
 * Endpoints:
 * - GET /api/v1/games/{gameId}/locations/latest - Get latest location per team (admin-only)
 */
@Path("/api/v1/games/{gameId}/locations")
@RolesAllowed("admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LocationResource {

    @Inject
    LocationDataRepository locationDataRepository;

    @Inject
    TeamRepository teamRepository;

    @Inject
    GameService gameService;

    /**
     * Get the latest location for each team in a game.
     * Returns a list of TeamLocationResponse DTOs with the most recent position per team.
     */
    @GET
    @Path("/latest")
    public Response getLatestTeamLocations(@PathParam("gameId") UUID gameId) {
        // Verify game exists
        if (gameService.findById(gameId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Game not found"))
                .build();
        }

        // Get all teams for this game, indexed by ID for name lookup
        List<Team> teams = teamRepository.findByGameId(gameId);
        Map<UUID, Team> teamMap = teams.stream()
            .collect(Collectors.toMap(Team::getId, t -> t));

        // Get latest location for each team
        List<TeamLocationResponse> locations = new ArrayList<>();
        for (Team team : teams) {
            locationDataRepository.findLatestByTeamId(team.getId())
                .ifPresent(loc -> {
                    double lat = loc.getLocation().getY();
                    double lng = loc.getLocation().getX();
                    Double speed = loc.getSpeed() != null ? loc.getSpeed().doubleValue() : null;
                    Double heading = loc.getHeading() != null ? loc.getHeading().doubleValue() : null;
                    String locationTime = loc.getLocationTime() != null ? loc.getLocationTime().toString() : null;

                    locations.add(new TeamLocationResponse(
                        team.getId(),
                        team.getName(),
                        lat,
                        lng,
                        speed,
                        heading,
                        locationTime
                    ));
                });
        }

        return Response.ok(locations).build();
    }
}

