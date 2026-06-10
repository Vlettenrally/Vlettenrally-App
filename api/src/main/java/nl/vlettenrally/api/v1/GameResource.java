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
import nl.vlettenrally.client.dto.legendstracking.EventCheckpoint;
import nl.vlettenrally.client.dto.legendstracking.EventDetailsBody;
import nl.vlettenrally.client.dto.legendstracking.EventDetailsResponse;
import nl.vlettenrally.client.dto.legendstracking.EventParticipant;
import nl.vlettenrally.client.exception.LocationApiException;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.data.dto.auth.GameCheckInLinkResponse;
import nl.vlettenrally.data.dto.game.EventSyncResponse;
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
import nl.vlettenrally.service.ImportResult;
import nl.vlettenrally.service.AuthService;
import nl.vlettenrally.service.TeamService;
import nl.vlettenrally.service.TeamImportService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
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
 * - POST /api/v1/games/{gameId}/import-checkpoints - Import checkpoints from location tracker
 */
@Path("/api/v1/games")
@RolesAllowed({"admin", "team"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GameResource {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

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
     *
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

        Checkpoint checkpoint;
        if ("polygon".equals(request.zoneType()) && request.polygonPoints() != null && request.polygonPoints().size() >= 3) {
            PolygonCheckpoint poly = new PolygonCheckpoint();
            poly.setZonePolygon(toPolygon(request.polygonPoints()));
            checkpoint = poly;
        } else {
            CircleCheckpoint circle = new CircleCheckpoint();
            if (request.radiusMeters() != null) {
                circle.setRadiusMeters(request.radiusMeters());
            }
            checkpoint = circle;
        }
        checkpoint.setGameId(gameId);
        checkpoint.setName(request.name());
        checkpoint.setPoints(request.points());
        checkpoint.setLatitude(request.latitude());
        checkpoint.setLongitude(request.longitude());

        if (request.openingTime() != null) {
            checkpoint.setOpeningTime(Instant.parse(request.openingTime()));
        }

        if (request.closingTime() != null) {
            checkpoint.setClosingTime(Instant.parse(request.closingTime()));
        }

        Checkpoint saved = checkpointService.save(checkpoint);
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

        Checkpoint existing = checkpointService.findById(checkpointId).orElse(null);
        if (existing == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Checkpoint not found"))
                .build();
        }

        // Determine if the zone type is changing
        boolean switchingToPolygon = "polygon".equals(request.zoneType())
            && request.polygonPoints() != null && request.polygonPoints().size() >= 3;
        boolean switchingToCircle = "circle".equals(request.zoneType()) || request.zoneType() == null;

        Checkpoint checkpoint;
        if (switchingToPolygon && !(existing instanceof PolygonCheckpoint)) {
            // Type changed from circle → polygon: create a new PolygonCheckpoint preserving common fields
            PolygonCheckpoint poly = new PolygonCheckpoint();
            copyCommonFields(existing, poly);
            poly.setZonePolygon(toPolygon(request.polygonPoints()));
            checkpoint = poly;
        } else if (switchingToCircle && !(existing instanceof CircleCheckpoint)) {
            // Type changed from polygon → circle: create a new CircleCheckpoint preserving common fields
            CircleCheckpoint circle = new CircleCheckpoint();
            copyCommonFields(existing, circle);
            if (request.radiusMeters() != null) {
                circle.setRadiusMeters(request.radiusMeters());
            }
            checkpoint = circle;
        } else {
            // Same type — update in place
            checkpoint = existing;
            if (checkpoint instanceof PolygonCheckpoint poly && request.polygonPoints() != null && request.polygonPoints().size() >= 3) {
                poly.setZonePolygon(toPolygon(request.polygonPoints()));
            } else if (checkpoint instanceof CircleCheckpoint circle && request.radiusMeters() != null) {
                circle.setRadiusMeters(request.radiusMeters());
            }
        }

        if (request.name() != null) checkpoint.setName(request.name());
        checkpoint.setPoints(request.points());
        checkpoint.setLatitude(request.latitude());
        checkpoint.setLongitude(request.longitude());
        if (request.openingTime() != null) {
            checkpoint.setOpeningTime(Instant.parse(request.openingTime()));
        }
        if (request.closingTime() != null) {
            checkpoint.setClosingTime(Instant.parse(request.closingTime()));
        }
        checkpoint.setUpdatedAt(Instant.now());

        Checkpoint saved = checkpointService.save(checkpoint);
        return Response.ok(toCheckpointDto(saved)).build();
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
     * Sync master data from the Legendstracking /details endpoint.
     * Upserts checkpoints by externalId ↔ EventCheckpoint.id, and links each
     * team's externalParticipantId by matching the participant bib to team.name
     * (case-insensitive trimmed; falls back to bib_label).
     */
    @POST
    @Path("/{gameId}/sync-event-details")
    @RolesAllowed("admin")
    public Response syncEventDetails(@PathParam("gameId") UUID gameId) {
        return gameService.findById(gameId)
            .map(game -> {
                if (game.getLocationEndpointUrl() == null || game.getLocationEndpointUrl().isBlank()) {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Game has no location endpoint configured"))
                        .build();
                }

                try {
                    EventDetailsResponse details = locationApiClient.fetchDetails(game.getLocationEndpointUrl());
                    EventDetailsBody body = details != null ? details.getEvent() : null;
                    if (body == null) {
                        return Response.status(Response.Status.BAD_GATEWAY)
                            .entity(new ErrorResponse("Malformed /details response: missing 'event'"))
                            .build();
                    }

                    int checkpointsCreated = 0;
                    int checkpointsUpdated = 0;
                    int checkpointErrors = 0;

                    List<EventCheckpoint> checkpoints = body.getEventCheckpoints();
                    if (checkpoints != null) {
                        for (EventCheckpoint detail : checkpoints) {
                            try {
                                String externalId = String.valueOf(detail.getId());
                                Optional<Checkpoint> existing = checkpointService.findByGameIdAndExternalId(gameId, externalId);

                                if (existing.isPresent()) {
                                    Checkpoint cp = existing.get();
                                    boolean changed = false;

                                    if (detail.getLocation() != null) {
                                        Double newLat = detail.getLocation().getLatitude();
                                        Double newLng = detail.getLocation().getLongitude();
                                        if (!java.util.Objects.equals(cp.getLatitude(), newLat)
                                                || !java.util.Objects.equals(cp.getLongitude(), newLng)) {
                                            cp.setLatitude(newLat);
                                            cp.setLongitude(newLng);
                                            changed = true;
                                        }
                                    }

                                    if (detail.getName() != null && !detail.getName().equals(cp.getName())) {
                                        cp.setName(detail.getName());
                                        changed = true;
                                    }

                                    if (changed) {
                                        cp.setUpdatedAt(Instant.now());
                                        checkpointService.save(cp);
                                        checkpointsUpdated++;
                                    }
                                } else {
                                    CircleCheckpoint cp = new CircleCheckpoint();
                                    cp.setGameId(gameId);
                                    cp.setExternalId(externalId);
                                    cp.setName(detail.getName() != null ? detail.getName() : "Zone " + externalId);
                                    cp.setPoints(0);
                                    if (detail.getLocation() != null) {
                                        cp.setLatitude(detail.getLocation().getLatitude());
                                        cp.setLongitude(detail.getLocation().getLongitude());
                                    }
                                    checkpointService.save(cp);
                                    checkpointsCreated++;
                                }
                            } catch (Exception e) {
                                checkpointErrors++;
                            }
                        }
                    }

                    int participantsLinked = 0;
                    int participantsUnmatched = 0;
                    List<EventParticipant> participants = body.getEventParticipants();
                    if (participants != null && !participants.isEmpty()) {
                        List<Team> teams = teamService.findByGameId(gameId);
                        java.util.Map<String, Team> teamsByName = new java.util.HashMap<>();
                        for (Team t : teams) {
                            if (t.getName() != null && !t.getName().isBlank()) {
                                teamsByName.put(t.getName().toLowerCase().trim(), t);
                            }
                        }

                        for (EventParticipant p : participants) {
                            Team match = lookupTeamForParticipant(teamsByName, p);
                            if (match == null) {
                                participantsUnmatched++;
                                continue;
                            }
                            if (!Integer.valueOf(p.getEventParticipantsId()).equals(match.getExternalParticipantId())) {
                                match.setExternalParticipantId(p.getEventParticipantsId());
                                match.setUpdatedAt(Instant.now());
                                teamService.save(match);
                            }
                            participantsLinked++;
                        }
                    }

                    return Response.ok(new EventSyncResponse(
                        checkpointsCreated, checkpointsUpdated, checkpointErrors,
                        participantsLinked, participantsUnmatched)).build();
                } catch (LocationApiException e) {
                    return Response.status(Response.Status.BAD_GATEWAY)
                        .entity(new ErrorResponse("Failed to fetch from location tracker: " + e.getMessage()))
                        .build();
                }
            })
            .orElse(gameNotFoundResponse());
    }

    private static Team lookupTeamForParticipant(java.util.Map<String, Team> teamsByName, EventParticipant p) {
        if (p.getBib() != null) {
            Team t = teamsByName.get(String.valueOf(p.getBib()).toLowerCase().trim());
            if (t != null) return t;
        }
        if (p.getBibLabel() != null && !p.getBibLabel().isBlank()) {
            Team t = teamsByName.get(p.getBibLabel().toLowerCase().trim());
            if (t != null) return t;
        }
        return null;
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
     * Copy common Checkpoint fields from one instance to another (used when zone type changes).
     */
    private void copyCommonFields(Checkpoint source, Checkpoint target) {
        target.setId(source.getId());
        target.setGameId(source.getGameId());
        target.setName(source.getName());
        target.setOpeningTime(source.getOpeningTime());
        target.setClosingTime(source.getClosingTime());
        target.setPoints(source.getPoints());
        target.setLatitude(source.getLatitude());
        target.setLongitude(source.getLongitude());
        target.setLocation(source.getLocation());
        target.setExternalId(source.getExternalId());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
    }

    /**
     * Convert a list of [lat, lng] pairs to a JTS Polygon.
     */
    private Polygon toPolygon(List<List<Double>> points) {
        // Build coordinate array: close the ring by repeating the first point
        Coordinate[] coords = new Coordinate[points.size() + 1];
        for (int i = 0; i < points.size(); i++) {
            List<Double> p = points.get(i);
            coords[i] = new Coordinate(p.get(1), p.get(0)); // JTS uses (x=lng, y=lat)
        }
        coords[points.size()] = coords[0]; // close the ring
        return GEOMETRY_FACTORY.createPolygon(coords);
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
