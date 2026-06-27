package nl.vlettenrally.api.v1;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.vlettenrally.api.security.PublicEndpointToken;
import nl.vlettenrally.data.dto.game.RankingResponse;
import nl.vlettenrally.service.GameService;
import nl.vlettenrally.service.RankingService;

import java.util.List;
import java.util.UUID;

/**
 * Public, read-only scoreboard API for published rankings.
 * <p>
 * Mirrors {@link PublicGalleryResource}: every endpoint is guarded by the shared
 * {@link PublicEndpointToken} secret ({@code X-Public-Endpoint-Token: <token>}) rather than JWT auth,
 * so a login-less external consumer (e.g. a big-screen scoreboard) can read standings.
 * {@code @PermitAll} bypasses the JWT role machinery; the token filter does the real gatekeeping.
 * The game listing a consumer picks from lives on {@link PublicResource}.
 * <p>
 * A ranking is only returned once an admin has published it (the award-ceremony reveal); until then
 * the endpoints answer with {@code 200 []} so a polling display degrades cleanly. The authenticated
 * {@link GameResource} ranking endpoint keeps its 403-until-published behavior for logged-in teams.
 */
@Path("/api/v1/public")
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
@PublicEndpointToken
public class PublicRankingResource {

    @Inject
    GameService gameService;

    @Inject
    RankingService rankingService;

    @Inject
    PublicRankingCache rankingCache;

    /**
     * Published ranking for a specific game. Returns an empty list when the game does not exist or
     * its rankings have not been published yet, so a polling display degrades cleanly without having
     * to distinguish error cases.
     */
    @GET
    @Path("/games/{gameId}/rankings")
    public Response rankingsForGame(@PathParam("gameId") UUID gameId) {
        List<RankingResponse> body = gameService.findById(gameId)
            .filter(g -> Boolean.TRUE.equals(g.getPublicRankingsEnabled()) || Boolean.TRUE.equals(g.getRankingsPublished()))
            .map(g -> rankingService.buildRanking(gameId))
            .orElseGet(List::of);
        return Response.ok(body).build();
    }

    /**
     * Published ranking for the most recent game that has one — so the consumer doesn't have to pick
     * a game. The literal {@code latest} segment is matched ahead of the {@code {gameId}} template by
     * JAX-RS, so it is never parsed as a UUID. Memoized for a short TTL by {@link PublicRankingCache}.
     */
    @GET
    @Path("/games/latest/rankings")
    public Response rankingsForLatestGame() {
        return Response.ok(rankingCache.latestPublishedRanking()).build();
    }
}
