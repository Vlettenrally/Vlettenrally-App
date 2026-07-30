package nl.vlettenrally.api.v1;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.vlettenrally.api.security.PublicMapToken;
import nl.vlettenrally.data.dto.common.ErrorResponse;

import java.util.UUID;

/**
 * Public, read-only live map for narrowcasting displays.
 * <p>
 * Narrowcasting software cannot log in — it opens a URL and leaves it running — so this endpoint is
 * guarded by the per-game {@link PublicMapToken} rather than JWT auth. {@code @PermitAll} bypasses
 * the JWT role machinery; the token filter does the real gatekeeping. Unlike its siblings
 * ({@link PublicGalleryResource}, {@link PublicRankingResource}) the token here is derived per game
 * instead of being the shared secret itself, because a map link is pasted into display config and
 * lives on screen: a leak should cost one game's map, not the whole public API.
 * <p>
 * Hidden zones are never included — see {@link PublicMapCache#mapFor}.
 */
@Path("/api/v1/public")
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
@PublicMapToken
public class PublicMapResource {

    @Inject
    PublicMapCache mapCache;

    /**
     * Live team positions and visible zones for a game. The path parameter is what the token is bound
     * to, so a token minted for another game never reaches this method.
     */
    @GET
    @Path("/games/{gameId}/map")
    public Response map(@PathParam("gameId") UUID gameId) {
        return mapCache.mapFor(gameId)
            .map(body -> Response.ok(body).build())
            .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Game not found"))
                .build());
    }
}
