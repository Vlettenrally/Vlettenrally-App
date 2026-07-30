package nl.vlettenrally.api.security;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import nl.vlettenrally.data.dto.common.ErrorResponse;

import java.util.UUID;

/**
 * JAX-RS filter that guards {@link PublicMapToken}-annotated endpoints with the per-game token
 * derived by {@link PublicMapTokenService}, supplied in the {@value #TOKEN_HEADER} header.
 * <p>
 * Unlike {@link PublicEndpointTokenFilter}, which compares against one shared secret, this filter
 * binds the presented token to the {@value #GAME_ID_PARAM} path parameter of the request being
 * served. A token minted for one game therefore fails on every other game's map. Name binding makes
 * this a post-matching filter, so the path parameters are already resolved by the time it runs.
 * <p>
 * Fails closed: if no secret is configured the endpoint is effectively disabled. Runs at
 * AUTHENTICATION priority since it establishes whether the caller is allowed at all.
 */
@PublicMapToken
@Provider
@Priority(Priorities.AUTHENTICATION)
public class PublicMapTokenFilter implements ContainerRequestFilter {

    /** Header carrying the per-game token. Kept off {@code Authorization} to avoid the JWT mechanism. */
    static final String TOKEN_HEADER = "X-Public-Map-Token";

    /** Path template the token is bound to. Must match the {@code {gameId}} segment on the resource. */
    static final String GAME_ID_PARAM = "gameId";

    @Inject
    PublicMapTokenService tokenService;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        // Fail closed: an unset/blank secret disables the public map entirely.
        if (!tokenService.isEnabled()) {
            abortUnauthorized(requestContext, "Public endpoints are not enabled");
            return;
        }

        String header = requestContext.getHeaderString(TOKEN_HEADER);
        if (header == null || header.isBlank()) {
            abortUnauthorized(requestContext, "Missing " + TOKEN_HEADER + " header");
            return;
        }

        UUID gameId = gameIdOf(requestContext);
        if (gameId == null) {
            // A malformed game id can't have a valid token; report it as such rather than leaking
            // that the path was well-formed but unknown.
            abortUnauthorized(requestContext, "Invalid token");
            return;
        }

        if (!tokenService.matches(gameId, header)) {
            abortUnauthorized(requestContext, "Invalid token");
        }
    }

    /** The {@code gameId} path parameter as a UUID, or null when absent or malformed. */
    private static UUID gameIdOf(ContainerRequestContext requestContext) {
        String raw = requestContext.getUriInfo().getPathParameters().getFirst(GAME_ID_PARAM);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void abortUnauthorized(ContainerRequestContext requestContext, String message) {
        requestContext.abortWith(
            Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse(message))
                .build()
        );
    }
}
