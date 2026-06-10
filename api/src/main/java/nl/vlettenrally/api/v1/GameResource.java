package nl.vlettenrally.api.v1;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import nl.vlettenrally.client.LocationApiClient;
import nl.vlettenrally.client.dto.TestConnectionResult;
import nl.vlettenrally.client.exception.LocationApiException;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.data.dto.auth.GameCheckInLinkResponse;
import nl.vlettenrally.data.dto.game.CheckpointImportResponse;
import nl.vlettenrally.data.dto.game.ParticipantLinkResponse;
import nl.vlettenrally.data.dto.game.CheckpointRequest;
import nl.vlettenrally.data.dto.game.CheckpointResponse;
import nl.vlettenrally.data.dto.game.GameRequest;
import nl.vlettenrally.data.dto.game.GameResponse;
import nl.vlettenrally.data.dto.game.LocationTestResponse;
import nl.vlettenrally.data.dto.game.TeamImportResponse;
import nl.vlettenrally.data.converter.TeamConverter;
import nl.vlettenrally.data.domain.Checkpoint;
import nl.vlettenrally.data.domain.CircleCheckpoint;
import nl.vlettenrally.data.domain.PolygonCheckpoint;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.dto.achievement.CheckpointTeamResponse;
import nl.vlettenrally.service.AchievementService;
import nl.vlettenrally.service.GameService;
import nl.vlettenrally.service.RankingService;
import nl.vlettenrally.service.CheckpointService;
import nl.vlettenrally.service.EventDetailsService;
import nl.vlettenrally.service.ImportResult;
import nl.vlettenrally.service.ParticipantLinkResult;
import nl.vlettenrally.service.AuthService;
import nl.vlettenrally.service.TeamService;
import nl.vlettenrally.service.TeamImportService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;

/**
 * REST Resource for Game operations
 * Endpoints:
 * - GET /api/v1/games - Get all games
 * - GET /api/v1/games/active - Get active and planned games
 * - GET /api/v1/games/{gameId} - Get game by ID
 * - POST /api/v1/games - Create a new game
 * - PUT /api/v1/games/{gameId} - Update a game
 * - DELETE /api/v1/games/{gameId} - Delete a game
 * - GET /api/v1/games/{gameId}/checkpoints - Get checkpoints for a game
 * - GET /api/v1/games/{gameId}/rankings - Get team rankings for a game
 * - POST /api/v1/games/{gameId}/import-teams - Import teams from pre-registration
 * - POST /api/v1/games/{gameId}/test-location-endpoint - Test location endpoint connection
 * - POST /api/v1/games/{gameId}/checkpoints - Create a checkpoint
 * - PUT /api/v1/games/{gameId}/checkpoints/{checkpointId} - Update a checkpoint
 * - DELETE /api/v1/games/{gameId}/checkpoints/{checkpointId} - Delete a checkpoint
 * - POST /api/v1/games/{gameId}/import-checkpoints - Import checkpoints (zones) from location tracker
 * - POST /api/v1/games/{gameId}/link-participants - Link teams to tracker participants
 */
@Path("/api/v1/games")
@RolesAllowed({"admin", "team"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GameResource {

    @Inject
    GameService gameService;

    @Inject
    CheckpointService checkpointService;

    @Inject
    TeamService teamService;

    @Inject
    TeamConverter teamConverter;

    @Inject
    TeamImportService teamImportService;

    @Inject
    EventDetailsService eventDetailsService;

    @Inject
    LocationApiClient locationApiClient;

    @Inject
    AuthService authService;

    @Inject
    AchievementService achievementService;

    @Inject
    RankingService rankingService;

    /**
     * Get all games
     */
    @GET
    public Response getAllGames() {
        List<Game> games = gameService.findAll();
        return Response.ok(games.stream().map(this::toDto).toList()).build();
    }

    /**
     * Get active and planned games
     */
    @GET
    @Path("/active")
    public Response getActiveGames() {
        List<Game> games = gameService.findActiveGames();
        return Response.ok(games.stream().map(this::toDto).toList()).build();
    }

    /**
     * Get count of games
     */
    @GET
    @Path("/count")
    public Response getGameCount() {
        long count = gameService.countGames();
        return Response.ok(count).build();
    }

    /**
     * Get game by ID
     */
    @GET
    @Path("/{gameId}")
    public Response getGameById(@PathParam("gameId") UUID gameId) {
        return gameService.findById(gameId)
            .map(game -> Response.ok(toDto(game)).build())
            .orElse(gameNotFoundResponse());
    }

    /**
     * Get checkpoints for a game
     */
    @GET
    @Path("/{gameId}/checkpoints")
    public Response getCheckpoints(@PathParam("gameId") UUID gameId) {
        // Verify game exists
        if (gameService.findById(gameId).isEmpty()) {
            return gameNotFoundResponse();
        }

        List<Checkpoint> checkpoints = checkpointService.findByGameId(gameId);
        return Response.ok(checkpoints.stream().map(this::toCheckpointDto).toList()).build();
    }

    /** Teams near a checkpoint: GPS within zone + any existing achievement */
    @GET
    @RolesAllowed({"admin", "checkpoint_manager"})
    @Path("/{gameId}/checkpoints/{checkpointId}/teams/nearby")
    public Response getTeamsNearCheckpoint(
            @PathParam("gameId") UUID gameId,
            @PathParam("checkpointId") UUID checkpointId) {
        try {
            List<CheckpointTeamResponse> teams = achievementService.getTeamsNearCheckpoint(gameId, checkpointId);
            return Response.ok(teams).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        }
    }

    /** Get teams for a game */
    @GET
    @Path("/{gameId}/teams")
    public Response getTeamsForGame(@PathParam("gameId") UUID gameId) {
        // Verify game exists
        if (gameService.findById(gameId).isEmpty()) {
            return gameNotFoundResponse();
        }
        List<Team> teams = teamService.findByGameId(gameId);
        return Response.ok(teams.stream().map(teamConverter::toDto).toList()).build();
    }

    /**
     * Generate a game-scoped check-in link for admin use
     */
    @POST
    @Path("/{gameId}/check-in-link")
    @RolesAllowed({"admin"})
    public Response generateCheckInLink(@PathParam("gameId") UUID gameId) {
        try {
            GameCheckInLinkResponse response = authService.generateGameCheckInLink(gameId);
            return Response.ok(response).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Failed to generate check-in link: " + e.getMessage()))
                .build();
        }
    }

    /**
     * Get team rankings for a game.
     * <p>
     * The full ranking exposes every team's score, so non-admin (team) callers
     * may only read it once the game has COMPLETED. Admins can always read it
     * (needed for points management during the game).
     */
    @GET
    @Path("/{gameId}/rankings")
    public Response getRankings(@PathParam("gameId") UUID gameId, @Context SecurityContext ctx) {
        // Verify game exists
        Optional<Game> game = gameService.findById(gameId);
        if (game.isEmpty()) {
            return gameNotFoundResponse();
        }

        if (!ctx.isUserInRole("admin") && game.get().getStatus() != Game.GameStatus.COMPLETED) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ErrorResponse("Rankings are available after the game has completed"))
                .build();
        }

        return Response.ok(rankingService.buildRanking(gameId)).build();
    }

    /**
     * Create a new game
     */
    @POST
    @RolesAllowed("admin")
    public Response createGame(GameRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("Game name is required"))
                .build();
        }

        Game game = new Game();
        game.setName(request.name());
        game.setDescription(request.description());
        game.setParticipantInfo(request.participantInfo());
        game.setStartTime(request.startTime());
        game.setEndTime(request.endTime());
        game.setCheckInStartTime(request.checkInStartTime());
        game.setStartSignalTime(request.startSignalTime());

        if (request.status() != null) {
            try {
                game.setStatus(Game.GameStatus.valueOf(request.status().toUpperCase()));
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Invalid status: " + request.status()))
                    .build();
            }
        }

        game.setExternalEventId(request.externalEventId());
        game.setExternalEventName(request.externalEventName());
        game.setLocationEndpointUrl(request.locationEndpointUrl());
        game.setLocationPollingFrequency(request.locationPollingFrequency() != null ? request.locationPollingFrequency() : 15);
        game.setLocationEndpointActive(request.locationEndpointActive() != null ? request.locationEndpointActive() : false);

        Game saved = gameService.save(game);
        return Response.status(Response.Status.CREATED).entity(toDto(saved)).build();
    }

    /**
     * Update an existing game
     */
    @PUT
    @Path("/{gameId}")
    @RolesAllowed("admin")
    public Response updateGame(@PathParam("gameId") UUID gameId, GameRequest request) {
        return gameService.findById(gameId)
            .map(game -> {
                if (request.name() != null) game.setName(request.name());
                if (request.description() != null) game.setDescription(request.description());
                if (request.participantInfo() != null) game.setParticipantInfo(request.participantInfo());
                if (request.startTime() != null) game.setStartTime(request.startTime());
                if (request.endTime() != null) game.setEndTime(request.endTime());
                if (request.checkInStartTime() != null) game.setCheckInStartTime(request.checkInStartTime());
                if (request.startSignalTime() != null) game.setStartSignalTime(request.startSignalTime());

                if (request.status() != null) {
                    try {
                        game.setStatus(Game.GameStatus.valueOf(request.status().toUpperCase()));
                    } catch (IllegalArgumentException e) {
                        // Ignore invalid status in update
                    }
                }

                if (request.externalEventId() != null) game.setExternalEventId(request.externalEventId());
                if (request.externalEventName() != null) game.setExternalEventName(request.externalEventName());
                if (request.locationEndpointUrl() != null) game.setLocationEndpointUrl(request.locationEndpointUrl());
                if (request.locationPollingFrequency() != null) game.setLocationPollingFrequency(request.locationPollingFrequency());
                if (request.locationEndpointActive() != null) game.setLocationEndpointActive(request.locationEndpointActive());

                Game updated = gameService.save(game);
                return Response.ok(toDto(updated)).build();
            })
            .orElse(gameNotFoundResponse());
    }

    /**
     * Update an existing game
     */
    @DELETE
    @Path("/{gameId}")
    @RolesAllowed("admin")
    public Response deleteGame(@PathParam("gameId") UUID gameId) {
        if (gameService.findById(gameId).isEmpty()) {
            return gameNotFoundResponse();
        }

        gameService.delete(gameId);
        return Response.noContent().build();
    }

    /**
     * Import teams from pre-registration system
     */
    @POST
    @Path("/{gameId}/import-teams")
    @RolesAllowed("admin")
    public Response importTeams(@PathParam("gameId") UUID gameId) {
        return gameService.findById(gameId)
            .map(game -> {
                if (game.getExternalEventId() == null) {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Game has no external event configured"))
                        .build();
                }

                try {
                    ImportResult result = teamImportService.importTeams(gameId, game.getExternalEventId());
                    return Response.ok(new TeamImportResponse(
                        result.created(),
                        result.updated(),
                        result.errors()
                    )).build();
                } catch (Exception e) {
                    return Response.status(Response.Status.BAD_GATEWAY)
                        .entity(new ErrorResponse("Import failed: " + e.getMessage()))
                        .build();
                }
            })
            .orElse(gameNotFoundResponse());
    }

    /**
     * Delete a game
     */
    @POST
    @Path("/{gameId}/test-location-endpoint")
    @RolesAllowed("admin")
    public Response testLocationEndpoint(@PathParam("gameId") UUID gameId) {
        return gameService.findById(gameId)
            .map(game -> {
                if (game.getLocationEndpointUrl() == null || game.getLocationEndpointUrl().isBlank()) {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Game has no location endpoint configured"))
                        .build();
                }

                try {
                    TestConnectionResult result =
                        locationApiClient.testConnection(game.getLocationEndpointUrl());
                    return Response.ok(new LocationTestResponse(
                        result.isSuccess(),
                        result.getParticipantCount(),
                        result.getErrorMessage()
                    )).build();
                } catch (LocationApiException e) {
                    return Response.ok(new LocationTestResponse(
                        false,
                        0,
                        e.getMessage()
                    )).build();
                }
            })
            .orElse(gameNotFoundResponse());
    }

    // Helper methods

    /**
     * Create a checkpoint for a game
     */
    @POST
    @Path("/{gameId}/checkpoints")
    @RolesAllowed("admin")
    public Response createCheckpoint(@PathParam("gameId") UUID gameId, CheckpointRequest request) {
        if (gameService.findById(gameId).isEmpty()) {
            return gameNotFoundResponse();
        }

        if (request.name() == null || request.name().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("Checkpoint name is required"))
                .build();
        }

        Checkpoint saved = checkpointService.create(gameId, request);
        return Response.status(Response.Status.CREATED).entity(toCheckpointDto(saved)).build();
    }

    /**
     * Update a checkpoint
     */
    @PUT
    @Path("/{gameId}/checkpoints/{checkpointId}")
    @RolesAllowed("admin")
    public Response updateCheckpoint(
            @PathParam("gameId") UUID gameId,
            @PathParam("checkpointId") UUID checkpointId,
            CheckpointRequest request) {

        return checkpointService.update(checkpointId, request)
            .map(saved -> Response.ok(toCheckpointDto(saved)).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Checkpoint not found"))
                .build());
    }

    /**
     * Delete a checkpoint
     */
    @DELETE
    @Path("/{gameId}/checkpoints/{checkpointId}")
    @RolesAllowed("admin")
    public Response deleteCheckpoint(
            @PathParam("gameId") UUID gameId,
            @PathParam("checkpointId") UUID checkpointId) {

        Optional<Checkpoint> existing = checkpointService.findById(checkpointId);
        if (existing.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Checkpoint not found"))
                .build();
        }

        checkpointService.delete(checkpointId);
        return Response.noContent().build();
    }

    /**
     * Import checkpoints (zones) from the Legendstracking /details endpoint.
     * Upserts checkpoints by externalId ↔ EventCheckpoint.id.
     */
    @POST
    @Path("/{gameId}/import-checkpoints")
    @RolesAllowed("admin")
    public Response importCheckpoints(@PathParam("gameId") UUID gameId) {
        return gameService.findById(gameId)
            .map(game -> {
                if (game.getLocationEndpointUrl() == null || game.getLocationEndpointUrl().isBlank()) {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Game has no location endpoint configured"))
                        .build();
                }

                try {
                    ImportResult result = eventDetailsService.importCheckpoints(gameId, game.getLocationEndpointUrl());
                    return Response.ok(new CheckpointImportResponse(
                        result.created(),
                        result.updated(),
                        result.errors()
                    )).build();
                } catch (LocationApiException e) {
                    return Response.status(Response.Status.BAD_GATEWAY)
                        .entity(new ErrorResponse("Failed to fetch from location tracker: " + e.getMessage()))
                        .build();
                }
            })
            .orElse(gameNotFoundResponse());
    }

    /**
     * Link teams to tracker participants from the Legendstracking /details endpoint.
     * Sets each team's externalParticipantId by matching the participant bib to
     * team.name (case-insensitive trimmed; falls back to bib_label).
     */
    @POST
    @Path("/{gameId}/link-participants")
    @RolesAllowed("admin")
    public Response linkParticipants(@PathParam("gameId") UUID gameId) {
        return gameService.findById(gameId)
            .map(game -> {
                if (game.getLocationEndpointUrl() == null || game.getLocationEndpointUrl().isBlank()) {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Game has no location endpoint configured"))
                        .build();
                }

                try {
                    ParticipantLinkResult result = eventDetailsService.linkParticipants(gameId, game.getLocationEndpointUrl());
                    return Response.ok(new ParticipantLinkResponse(
                        result.linked(),
                        result.unmatched()
                    )).build();
                } catch (LocationApiException e) {
                    return Response.status(Response.Status.BAD_GATEWAY)
                        .entity(new ErrorResponse("Failed to fetch from location tracker: " + e.getMessage()))
                        .build();
                }
            })
            .orElse(gameNotFoundResponse());
    }

    private GameResponse toDto(Game game) {
        return new GameResponse(
            game.getId(),
            game.getName(),
            game.getDescription(),
            game.getParticipantInfo(),
            game.getStartTime() != null ? game.getStartTime().toString() : null,
            game.getEndTime() != null ? game.getEndTime().toString() : null,
            game.getCheckInStartTime() != null ? game.getCheckInStartTime().toString() : null,
            game.getStartSignalTime() != null ? game.getStartSignalTime().toString() : null,
            game.getStatus() != null ? game.getStatus().name().toLowerCase() : null,
            game.getExternalEventId(),
            game.getExternalEventName(),
            game.getLocationEndpointUrl(),
            game.getLocationPollingFrequency(),
            game.getLocationEndpointActive(),
            game.getCreatedAt() != null ? game.getCreatedAt().toString() : null,
            game.getUpdatedAt() != null ? game.getUpdatedAt().toString() : null
        );
    }

    private CheckpointResponse toCheckpointDto(Checkpoint checkpoint) {
        double radiusMeters = 0;
        List<List<Double>> polygonPoints = null;

        if (checkpoint instanceof CircleCheckpoint circle) {
            radiusMeters = circle.getRadiusMeters();
        } else if (checkpoint instanceof PolygonCheckpoint polygon) {
            polygonPoints = fromPolygon(polygon.getZonePolygon());
        }

        return new CheckpointResponse(
            checkpoint.getId(),
            checkpoint.getGameId(),
            checkpoint.getName(),
            checkpoint.getOpeningTime() != null ? checkpoint.getOpeningTime().toString() : null,
            checkpoint.getClosingTime() != null ? checkpoint.getClosingTime().toString() : null,
            checkpoint.getPoints(),
            checkpoint.getLatitude(),
            checkpoint.getLongitude(),
            checkpoint.getExternalId(),
            checkpoint.getZoneType(),
            radiusMeters,
            polygonPoints
        );
    }

    private Response gameNotFoundResponse() {
        return Response.status(Response.Status.NOT_FOUND)
            .entity(new ErrorResponse("Game not found"))
            .build();
    }

    /**
     * Extract polygon vertices as a list of [lat, lng] pairs from a JTS Polygon.
     * Excludes the closing point (which duplicates the first).
     */
    private List<List<Double>> fromPolygon(Polygon polygon) {
        if (polygon == null) return null;
        Coordinate[] coords = polygon.getExteriorRing().getCoordinates();
        List<List<Double>> result = new ArrayList<>();
        // Skip the last coordinate (closing point that duplicates the first)
        for (int i = 0; i < coords.length - 1; i++) {
            result.add(List.of(coords[i].getY(), coords[i].getX())); // [lat, lng]
        }
        return result;
    }
}
