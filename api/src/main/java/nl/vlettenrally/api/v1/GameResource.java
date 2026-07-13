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
import nl.vlettenrally.api.security.SecurityRoles;
import nl.vlettenrally.client.LocationApiClient;
import nl.vlettenrally.client.dto.TestConnectionResult;
import nl.vlettenrally.client.exception.LocationApiException;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.data.dto.auth.GameCheckInLinkResponse;
import nl.vlettenrally.data.dto.game.CheckpointImportResponse;
import nl.vlettenrally.data.dto.game.CheckpointRequest;
import nl.vlettenrally.data.dto.game.CheckpointResponse;
import nl.vlettenrally.data.dto.game.CopyCheckpointsRequest;
import nl.vlettenrally.data.dto.game.CopyCheckpointsResult;
import nl.vlettenrally.data.dto.game.GameRequest;
import nl.vlettenrally.data.dto.game.GameResponse;
import nl.vlettenrally.data.dto.game.TeamGameResponse;
import nl.vlettenrally.data.dto.game.LocationTestResponse;
import nl.vlettenrally.data.dto.game.TeamImportResponse;
import nl.vlettenrally.data.dto.game.AssignTrackersResponse;
import nl.vlettenrally.data.dto.game.BackfillResponse;
import nl.vlettenrally.data.converter.TeamConverter;
import nl.vlettenrally.data.domain.Checkpoint;
import nl.vlettenrally.data.domain.CircleCheckpoint;
import nl.vlettenrally.data.domain.PolygonCheckpoint;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.dto.achievement.CheckpointTeamResponse;
import nl.vlettenrally.service.AchievementService;
import nl.vlettenrally.service.AutomationEngine;
import nl.vlettenrally.service.LocationGuardConfig;
import nl.vlettenrally.service.LocationBackfillService;
import nl.vlettenrally.service.LocationIngestService;
import nl.vlettenrally.service.AutomationService;
import nl.vlettenrally.service.GameService;
import nl.vlettenrally.service.RankingService;
import nl.vlettenrally.service.CheckpointService;
import nl.vlettenrally.service.EventDetailsService;
import nl.vlettenrally.service.ImportResult;
import nl.vlettenrally.service.TeamImportResult;
import nl.vlettenrally.service.TrackerAssignmentResult;
import nl.vlettenrally.service.TrackerAssignmentService;
import nl.vlettenrally.service.AuthService;
import nl.vlettenrally.service.TeamService;
import nl.vlettenrally.service.TeamImportService;
import nl.vlettenrally.websockets.WebSocketPublisher;
import nl.vlettenrally.websockets.dto.WebSocketMessageType;

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
 */
@Path("/api/v1/games")
@RolesAllowed({SecurityRoles.STAFF, SecurityRoles.TEAM})
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
    TrackerAssignmentService trackerAssignmentService;

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

    @Inject
    AutomationService automationService;

    @Inject
    AutomationEngine automationEngine;

    @Inject
    LocationIngestService locationIngestService;

    @Inject
    LocationBackfillService locationBackfillService;

    @Inject
    LocationGuardConfig locationGuardConfig;

    @Inject
    WebSocketPublisher webSocketPublisher;

    /**
     * Get all games
     */
    @GET
    public Response getAllGames(@Context SecurityContext ctx) {
        List<Game> games = gameService.findAll();
        boolean isStaff = ctx.isUserInRole(SecurityRoles.STAFF);
        return Response.ok(games.stream()
            .map(g -> isStaff ? toDto(g) : toTeamDto(g))
            .toList()).build();
    }

    /**
     * Get active and planned games
     */
    @GET
    @Path("/active")
    public Response getActiveGames(@Context SecurityContext ctx) {
        List<Game> games = gameService.findActiveGames();
        boolean isStaff = ctx.isUserInRole(SecurityRoles.STAFF);
        return Response.ok(games.stream()
            .map(g -> isStaff ? toDto(g) : toTeamDto(g))
            .toList()).build();
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
    public Response getGameById(@PathParam("gameId") UUID gameId, @Context SecurityContext ctx) {
        boolean isStaff = ctx.isUserInRole(SecurityRoles.STAFF);
        return gameService.findById(gameId)
            .map(game -> Response.ok(isStaff ? toDto(game) : toTeamDto(game)).build())
            .orElse(gameNotFoundResponse());
    }

    /**
     * Get checkpoints for a game
     */
    @GET
    @Path("/{gameId}/checkpoints")
    public Response getCheckpoints(@PathParam("gameId") UUID gameId, @Context SecurityContext ctx) {
        // Verify game exists
        if (gameService.findById(gameId).isEmpty()) {
            return gameNotFoundResponse();
        }

        // Hidden zones drive automations but must not appear on teams' map.
        boolean isAdmin = ctx.isUserInRole(SecurityRoles.STAFF);
        List<Checkpoint> checkpoints = checkpointService.findByGameId(gameId);
        return Response.ok(checkpoints.stream()
            .filter(cp -> isAdmin || !cp.isHidden())
            .map(cp -> toCheckpointDto(cp, isAdmin))
            .toList()).build();
    }

    /** Teams near a checkpoint: GPS within zone + any existing achievement */
    @GET
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.CHECKPOINT_MANAGER})
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
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.GAME_MASTER})
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
     * may only read it once an admin has published the final standings (the
     * award-ceremony reveal). Admins can always read it (needed for points
     * management during the game and to announce the standings).
     */
    @GET
    @Path("/{gameId}/rankings")
    public Response getRankings(@PathParam("gameId") UUID gameId, @Context SecurityContext ctx) {
        // Verify game exists
        Optional<Game> game = gameService.findById(gameId);
        if (game.isEmpty()) {
            return gameNotFoundResponse();
        }

        if (!ctx.isUserInRole(SecurityRoles.STAFF) && !Boolean.TRUE.equals(game.get().getRankingsPublished())) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ErrorResponse("Rankings have not been published yet"))
                .build();
        }

        return Response.ok(rankingService.buildRanking(gameId)).build();
    }

    /**
     * Publish (reveal) the final ranking to players.
     * <p>
     * Admin-only. Persists the reveal on the game so it survives players
     * reloading/reconnecting, and pushes the full ranking to all connected teams.
     * Idempotent: re-publishing just re-broadcasts the latest standings (useful
     * if points were corrected mid-ceremony).
     */
    @POST
    @Path("/{gameId}/rankings/publish")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.GAME_MASTER})
    public Response publishRankings(@PathParam("gameId") UUID gameId) {
        return gameService.findById(gameId)
            .map(game -> {
                game.setRankingsPublished(true);
                Game updated = gameService.save(game);
                webSocketPublisher.broadcastToGame(
                    gameId,
                    WebSocketMessageType.RANKING_UPDATE.getValue(),
                    rankingService.buildRanking(gameId));
                return Response.ok(toDto(updated)).build();
            })
            .orElse(gameNotFoundResponse());
    }

    /**
     * Create a new game
     */
    @POST
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.GAME_MASTER})
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
        game.setLocationPollingLeadMinutes(request.locationPollingLeadMinutes() != null ? request.locationPollingLeadMinutes() : 30);
        game.setLocationPollingTailMinutes(request.locationPollingTailMinutes() != null ? request.locationPollingTailMinutes() : 30);

        // Per-game guard overrides: null leaves the game inheriting the global default.
        game.setLocMaxSpeedKmh(request.locMaxSpeedKmh());
        game.setLocBoatMaxSpeedKmh(request.locBoatMaxSpeedKmh());
        game.setLocTurnAngleThresholdDegrees(request.locTurnAngleThresholdDegrees());
        game.setLocMinBearingSegmentMeters(request.locMinBearingSegmentMeters());
        game.setLocMaxAccuracyMeters(request.locMaxAccuracyMeters());
        game.setLocStaleFixSeconds(request.locStaleFixSeconds());

        Game saved = gameService.save(game);
        // Every game carries the built-in distance-points automation.
        automationService.createSystemDistanceRule(saved.getId());
        return Response.status(Response.Status.CREATED).entity(toDto(saved)).build();
    }

    /**
     * Update an existing game
     */
    @PUT
    @Path("/{gameId}")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.GAME_MASTER})
    public Response updateGame(@PathParam("gameId") UUID gameId, GameRequest request) {
        return gameService.findById(gameId)
            .map(game -> {
                Game.GameStatus oldStatus = game.getStatus();
                if (request.name() != null) game.setName(request.name());
                if (request.description() != null) game.setDescription(request.description());
                if (request.participantInfo() != null) game.setParticipantInfo(request.participantInfo());
                if (request.startTime() != null) game.setStartTime(request.startTime());
                if (request.endTime() != null) game.setEndTime(request.endTime());
                if (request.checkInStartTime() != null) game.setCheckInStartTime(request.checkInStartTime());

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
                if (request.locationPollingLeadMinutes() != null) game.setLocationPollingLeadMinutes(request.locationPollingLeadMinutes());
                if (request.locationPollingTailMinutes() != null) game.setLocationPollingTailMinutes(request.locationPollingTailMinutes());

                // Per-game guard overrides: null = "leave unchanged" (consistent with the
                // other fields above). Clearing back to "inherit global" is a DB-level action.
                if (request.locMaxSpeedKmh() != null) game.setLocMaxSpeedKmh(request.locMaxSpeedKmh());
                if (request.locBoatMaxSpeedKmh() != null) game.setLocBoatMaxSpeedKmh(request.locBoatMaxSpeedKmh());
                if (request.locTurnAngleThresholdDegrees() != null) game.setLocTurnAngleThresholdDegrees(request.locTurnAngleThresholdDegrees());
                if (request.locMinBearingSegmentMeters() != null) game.setLocMinBearingSegmentMeters(request.locMinBearingSegmentMeters());
                if (request.locMaxAccuracyMeters() != null) game.setLocMaxAccuracyMeters(request.locMaxAccuracyMeters());
                if (request.locStaleFixSeconds() != null) game.setLocStaleFixSeconds(request.locStaleFixSeconds());

                Game updated = gameService.save(game);
                // Guard config may have changed; evict this replica's cached thresholds at once.
                locationGuardConfig.invalidate(updated.getId());
                if (updated.getStatus() != oldStatus) {
                    // Manual admin transition — the scheduler hook won't see it.
                    automationEngine.onGameStatusChange(updated, oldStatus, updated.getStatus());
                    if (updated.getStatus() == Game.GameStatus.ACTIVE
                            && oldStatus != Game.GameStatus.ACTIVE) {
                        // Fire automations for teams already sitting inside a zone at kickoff.
                        locationIngestService.evaluateCurrentPositions(updated.getId());
                    }
                }
                return Response.ok(toDto(updated)).build();
            })
            .orElse(gameNotFoundResponse());
    }

    /**
     * Update an existing game
     */
    @DELETE
    @Path("/{gameId}")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.GAME_MASTER})
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
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.GAME_MASTER})
    public Response importTeams(@PathParam("gameId") UUID gameId) {
        return gameService.findById(gameId)
            .map(game -> {
                if (game.getExternalEventId() == null) {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Game has no external event configured"))
                        .build();
                }

                if (game.getStatus() == Game.GameStatus.COMPLETED) {
                    return Response.status(Response.Status.CONFLICT)
                        .entity(new ErrorResponse("Cannot import teams for a completed game"))
                        .build();
                }

                try {
                    TeamImportResult result = teamImportService.importTeams(gameId, game.getExternalEventId());
                    return Response.ok(new TeamImportResponse(
                        result.created(),
                        result.updated(),
                        result.skipped(),
                        result.errors(),
                        result.createdTeams(),
                        result.updatedTeams(),
                        result.skippedDuplicates(),
                        result.errorDetails()
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
     * Backfill a single team's historical track from the Legendstracking per-participant history
     * endpoint, to fill gaps left when the tracker was offline. The provider endpoint is expensive,
     * so this is throttled by a shared per-team cooldown (429 while cooling down).
     */
    @POST
    @Path("/{gameId}/teams/{teamId}/backfill-track")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.GAME_MASTER})
    public Response backfillTeamTrack(@PathParam("gameId") UUID gameId, @PathParam("teamId") UUID teamId) {
        if (gameService.findById(gameId).isEmpty()) {
            return gameNotFoundResponse();
        }
        try {
            LocationBackfillService.BackfillResult result =
                locationBackfillService.backfillTeamTrack(gameId, teamId);
	        return switch(result.status()) {
		        case COMPLETED -> Response.ok(new BackfillResponse(
				        result.inserted(), result.droppedByGuard(), result.checkpointPassesDetected())).build();
		        case COOLING_DOWN -> Response.status(429)
				        .header("Retry-After", result.cooldownRemainingSeconds())
				        .entity(new ErrorResponse("Backfill is cooling down; retry in "
						        + result.cooldownRemainingSeconds() + "s"))
				        .build();
		        case BUSY -> Response.status(Response.Status.CONFLICT)
				        .entity(new ErrorResponse("A backfill for this team is already running")).build();
		        case NOT_CONFIGURED -> Response.status(Response.Status.BAD_REQUEST)
				        .entity(new ErrorResponse("Game has no location endpoint configured")).build();
		        default -> Response.status(Response.Status.BAD_REQUEST)
				        .entity(new ErrorResponse(
						        "Team has no tracker, or its tracker is not reporting to the event")).build();
	        };
        } catch (LocationApiException e) {
            return Response.status(Response.Status.BAD_GATEWAY)
                .entity(new ErrorResponse("Backfill failed: " + e.getMessage())).build();
        }
    }

    /**
     * Auto-assign free trackers from the global pool to this game's teams.
     * Trackers are handed out to teams without one yet; re-running is a no-op for
     * IDs already in use. The pool itself is managed via /api/v1/trackers.
     */
    @POST
    @Path("/{gameId}/assign-trackers")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.RALLY_DESK})
    public Response assignTrackers(@PathParam("gameId") UUID gameId) {
        return gameService.findById(gameId)
            .map(game -> {
                TrackerAssignmentResult result = trackerAssignmentService.assignTrackers(gameId);
                return Response.ok(new AssignTrackersResponse(
                    result.assigned(),
                    result.teamsWithoutTracker(),
                    result.unused(),
                    result.assignedPairs(),
                    result.teamsWithoutTrackerNames(),
                    result.unusedTrackers()
                )).build();
            })
            .orElse(gameNotFoundResponse());
    }

    /**
     * Enabled trackers in the global pool that are not assigned to any team in a
     * non-completed game — the options for the per-team tracker picker.
     */
    @GET
    @Path("/{gameId}/trackers/available")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.RALLY_DESK})
    public Response availableTrackers(@PathParam("gameId") UUID gameId) {
        if (gameService.findById(gameId).isEmpty()) {
            return gameNotFoundResponse();
        }
        return Response.ok(trackerAssignmentService.availableTrackers(gameId)).build();
    }

    /**
     * Export the current tracker -> boat mapping as CSV to send to the provider.
     */
    @GET
    @Path("/{gameId}/trackers/export")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.RALLY_DESK})
    @Produces("text/csv")
    public Response exportTrackers(@PathParam("gameId") UUID gameId) {
        if (gameService.findById(gameId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        String csv = trackerAssignmentService.exportCsv(gameId);
        return Response.ok(csv)
            .header("Content-Disposition", "attachment; filename=\"trackers-" + gameId + ".csv\"")
            .build();
    }

    /**
     * Delete a game
     */
    @POST
    @Path("/{gameId}/test-location-endpoint")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.GAME_MASTER})
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
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.GAME_MASTER})
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
        return Response.status(Response.Status.CREATED).entity(toCheckpointDto(saved, true)).build();
    }

    /**
     * Update a checkpoint
     */
    @PUT
    @Path("/{gameId}/checkpoints/{checkpointId}")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.GAME_MASTER})
    public Response updateCheckpoint(
            @PathParam("gameId") UUID gameId,
            @PathParam("checkpointId") UUID checkpointId,
            CheckpointRequest request) {

        return checkpointService.update(checkpointId, request)
            .map(saved -> Response.ok(toCheckpointDto(saved, true)).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Checkpoint not found"))
                .build());
    }

    /**
     * Delete a checkpoint
     */
    @DELETE
    @Path("/{gameId}/checkpoints/{checkpointId}")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.GAME_MASTER})
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
     * Copy all zones from another game into this game, shifting opening/closing
     * times into this game's timeframe and preserving the hidden flag.
     */
    @POST
    @Path("/{gameId}/copy-checkpoints")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.GAME_MASTER})
    public Response copyCheckpoints(@PathParam("gameId") UUID gameId, CopyCheckpointsRequest request) {
        if (request == null || request.sourceGameId() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("sourceGameId is required"))
                .build();
        }
        CopyCheckpointsResult result = checkpointService.copyCheckpoints(gameId, request.sourceGameId());
        return Response.ok(result).build();
    }

    /**
     * Import checkpoints (zones) from the Legendstracking /details endpoint.
     * Upserts checkpoints by externalId ↔ EventCheckpoint.id.
     */
    @POST
    @Path("/{gameId}/import-checkpoints")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.GAME_MASTER})
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

    private GameResponse toDto(Game game) {
        return new GameResponse(
            game.getId(),
            game.getName(),
            game.getDescription(),
            game.getParticipantInfo(),
            game.getStartTime() != null ? game.getStartTime().toString() : null,
            game.getEndTime() != null ? game.getEndTime().toString() : null,
            game.getCheckInStartTime() != null ? game.getCheckInStartTime().toString() : null,
            game.getStatus() != null ? game.getStatus().name().toLowerCase() : null,
            game.getExternalEventId(),
            game.getExternalEventName(),
            game.getLocationEndpointUrl(),
            game.getLocationPollingFrequency(),
            game.getLocationEndpointActive(),
            game.getLocationPollingLeadMinutes(),
            game.getLocationPollingTailMinutes(),
            game.getLocMaxSpeedKmh(),
            game.getLocBoatMaxSpeedKmh(),
            game.getLocTurnAngleThresholdDegrees(),
            game.getLocMinBearingSegmentMeters(),
            game.getLocMaxAccuracyMeters(),
            game.getLocStaleFixSeconds(),
            game.getRankingsPublished() != null && game.getRankingsPublished(),
            game.getPublicRankingsEnabled() != null && game.getPublicRankingsEnabled(),
            game.getCreatedAt() != null ? game.getCreatedAt().toString() : null,
            game.getUpdatedAt() != null ? game.getUpdatedAt().toString() : null
        );
    }

    private CheckpointResponse toCheckpointDto(Checkpoint checkpoint, boolean isAdmin) {
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
            isAdmin ? checkpoint.getExternalId() : null,
            checkpoint.getZoneType(),
            radiusMeters,
            polygonPoints,
            checkpoint.isHidden(),
            checkpoint.getColor()
        );
    }

    private TeamGameResponse toTeamDto(Game game) {
        return new TeamGameResponse(
            game.getId(),
            game.getName(),
            game.getDescription(),
            game.getParticipantInfo(),
            game.getStartTime() != null ? game.getStartTime().toString() : null,
            game.getEndTime() != null ? game.getEndTime().toString() : null,
            game.getCheckInStartTime() != null ? game.getCheckInStartTime().toString() : null,
            game.getStatus() != null ? game.getStatus().name().toLowerCase() : null,
            game.getRankingsPublished() != null && game.getRankingsPublished(),
            game.getPublicRankingsEnabled() != null && game.getPublicRankingsEnabled()
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
