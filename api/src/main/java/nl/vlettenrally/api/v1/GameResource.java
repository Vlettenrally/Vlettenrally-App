package nl.vlettenrally.api.v1;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.data.dto.game.CheckpointResponse;
import nl.vlettenrally.data.dto.game.GameResponse;
import nl.vlettenrally.data.dto.game.RankingResponse;
import nl.vlettenrally.data.dto.team.TeamResponse;
import nl.vlettenrally.data.domain.Checkpoint;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.service.GameService;
import nl.vlettenrally.service.CheckpointService;
import nl.vlettenrally.service.TeamService;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * REST Resource for Game operations
 * 
 * Endpoints:
 * - GET /api/v1/games - Get all games
 * - GET /api/v1/games/active - Get active and planned games
 * - GET /api/v1/games/{gameId} - Get game by ID
 * - GET /api/v1/games/{gameId}/checkpoints - Get checkpoints for a game
 * - GET /api/v1/games/{gameId}/rankings - Get team rankings for a game
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
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Game not found"))
                .build());
    }

    /**
     * Get checkpoints for a game
     */
    @GET
    @Path("/{gameId}/checkpoints")
    public Response getCheckpoints(@PathParam("gameId") UUID gameId) {
        // Verify game exists
        if (gameService.findById(gameId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Game not found"))
                .build();
        }

        List<Checkpoint> checkpoints = checkpointService.findByGameId(gameId);
        return Response.ok(checkpoints.stream().map(this::toCheckpointDto).toList()).build();
    }

    /** Get teams for a game */
    @GET
    @Path("/{gameId}/teams")
    public Response getTeamsForGame(@PathParam("gameId") UUID gameId) {
        // Verify game exists
        if (gameService.findById(gameId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Game not fo  und"))
                    .build();
        }
        List<Team> teams = teamService.findByGameId(gameId);
        return Response.ok(teams.stream().map(team -> new TeamResponse(
                team.getId(),
                gameId, team.getName(),
                null,
                team.getBoatNumber(),
                null, null, null, team.isCheckedIn(),
                team.getPoints(), null, null, null, null, null
        )).toList()).build();
    }

    /**
     * Get team rankings for a game
     */
    @GET
    @Path("/{gameId}/rankings")
    public Response getRankings(@PathParam("gameId") UUID gameId) {
        // Verify game exists
        if (gameService.findById(gameId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Game not found"))
                .build();
        }

        List<Team> teams = teamService.findByGameId(gameId);
        
        // Sort by points descending
        List<RankingResponse> rankings = teams.stream()
            .filter(Team::isCheckedIn) // Only include checked-in teams
            .sorted(Comparator.comparingInt(Team::getPoints).reversed())
            .map(team -> {
                int position = 1;
                for (Team t : teams) {
                    if (t.isCheckedIn() && t.getPoints() > team.getPoints()) {
                        position++;
                    }
                }
                return new RankingResponse(
                    position,
                    team.getId(),
                    team.getName(),
                    team.getBoatNumber(),
                    team.getPoints()
                );
            })
            .toList();

        return Response.ok(rankings).build();
    }

    // Helper methods

    private GameResponse toDto(Game game) {
        return new GameResponse(
            game.getId(),
            game.getName(),
            game.getDescription(),
            game.getStartTime() != null ? game.getStartTime().toString() : null,
            game.getEndTime() != null ? game.getEndTime().toString() : null,
            game.getStartingAreaActiveTime() != null ? game.getStartingAreaActiveTime().toString() : null,
            game.getStartSignalTime() != null ? game.getStartSignalTime().toString() : null,
            game.getStatus() != null ? game.getStatus().name().toLowerCase() : null
        );
    }

    private CheckpointResponse toCheckpointDto(Checkpoint checkpoint) {
        return new CheckpointResponse(
            checkpoint.getId(),
            checkpoint.getGameId(),
            checkpoint.getName(),
            checkpoint.getOpeningTime() != null ? checkpoint.getOpeningTime().toString() : null,
            checkpoint.getClosingTime() != null ? checkpoint.getClosingTime().toString() : null,
            checkpoint.getPoints(),
            checkpoint.getLatitude(),
            checkpoint.getLongitude()
        );
    }
}
