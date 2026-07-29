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
import nl.vlettenrally.data.domain.Picture;
import nl.vlettenrally.service.PictureService;

import java.util.List;
import java.util.UUID;

/**
 * Public, read-only gallery API for approved pictures.
 * <p>
 * Unlike {@link PictureResource} (team/admin JWT auth), every endpoint here is guarded by a single
 * shared secret ({@link PublicEndpointToken}, {@code X-Public-Endpoint-Token: <token>}) so an external,
 * embeddable consumer (e.g. an event slideshow) can read approved pictures without a login.
 * {@code @PermitAll} bypasses the JWT role machinery; the token filter does the real gatekeeping.
 * The game listing a consumer picks from lives on {@link PublicResource}.
 * <p>
 * Only APPROVED pictures are ever returned, and view URLs come from the cached long-lived
 * pre-signed URL path ({@link PictureService#viewUrlFor}).
 */
@Path("/api/v1/public")
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
@PublicEndpointToken
public class PublicGalleryResource {

    @Inject
    PictureService pictureService;

    @Inject
    PublicGalleryCache galleryCache;

    /** Approved pictures for a specific game, newest first. */
    @GET
    @Path("/games/{gameId}/pictures")
    public Response approvedForGame(@PathParam("gameId") UUID gameId) {
        return pictures(pictureService.findApprovedByGame(gameId));
    }

    /**
     * Approved pictures for the most recent game that has any, newest first — so the consumer
     * doesn't have to pick a game. The literal {@code latest} segment is matched ahead of the
     * {@code {gameId}} template by JAX-RS, so it is never parsed as a UUID.
     * <p>
     * The response is memoized for a short TTL by {@link PublicGalleryCache}, since this
     * parameter-less endpoint is the hottest one in the gallery API.
     */
    @GET
    @Path("/games/latest/pictures")
    public Response approvedForLatestGame() {
        return Response.ok(galleryCache.latestApprovedGallery()).build();
    }

    /** All approved pictures across every game, newest first. */
    @GET
    @Path("/pictures")
    public Response allApproved() {
        return pictures(pictureService.findAllApproved());
    }

    private Response pictures(List<Picture> pics) {
        return Response.ok(pictureService.toPublicResponses(pics)).build();
    }
}
