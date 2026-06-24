package nl.vlettenrally.api.v1;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.vlettenrally.api.security.PublicEndpointToken;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.dto.game.PublicGameResponse;
import nl.vlettenrally.service.GameService;

import java.util.Comparator;
import java.util.List;

/**
 * Public, read-only API for resources shared across the public consumers — currently the game
 * listing, so a consumer can pick a game to fetch pictures ({@link PublicGalleryResource}) or
 * rankings ({@link PublicRankingResource}) for.
 * <p>
 * Like its siblings, every endpoint is guarded by the shared {@link PublicEndpointToken} secret
 * ({@code X-Public-Endpoint-Token: <token>}) rather than JWT auth, so a login-less external consumer
 * can read it. {@code @PermitAll} bypasses the JWT role machinery; the token filter does the real
 * gatekeeping. The {@code /api/v1/public} base path is shared with the gallery and ranking
 * resources; JAX-RS dispatches on the full (distinct) sub-paths.
 */
@Path("/api/v1/public")
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
@PublicEndpointToken
public class PublicResource {

    @Inject
    GameService gameService;

    /** List games (minimal projection) so a consumer can pick one. Most recent first. */
    @GET
    @Path("/games")
    public Response games() {
        List<PublicGameResponse> body = gameService.findAll().stream()
            .sorted(Comparator.comparing(Game::getStartTime,
                Comparator.nullsLast(Comparator.reverseOrder())))
            .map(PublicGameResponse::from)
            .toList();
        return Response.ok(body).build();
    }
}
