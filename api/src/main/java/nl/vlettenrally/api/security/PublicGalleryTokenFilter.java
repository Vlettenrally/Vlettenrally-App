package nl.vlettenrally.api.security;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import nl.vlettenrally.config.PublicGalleryConfig;
import nl.vlettenrally.data.dto.common.ErrorResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * JAX-RS filter that guards {@link PublicGalleryToken}-annotated endpoints with a single shared
 * secret supplied as {@code Authorization: Bearer <token>}.
 * <p>
 * Fails closed: if no token is configured the endpoint is effectively disabled. Runs at
 * AUTHENTICATION priority since it establishes whether the caller is allowed at all.
 */
@PublicGalleryToken
@Provider
@Priority(Priorities.AUTHENTICATION)
public class PublicGalleryTokenFilter implements ContainerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    @Inject
    PublicGalleryConfig config;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        // Fail closed: an unset/blank token disables the public gallery entirely.
        String expected = config.token().map(String::trim).filter(t -> !t.isEmpty()).orElse(null);
        if (expected == null) {
            abortUnauthorized(requestContext, "Public gallery is not enabled");
            return;
        }

        String header = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            abortUnauthorized(requestContext, "Missing or malformed Authorization header");
            return;
        }

        String presented = header.substring(BEARER_PREFIX.length()).trim();
        if (!constantTimeEquals(presented, expected)) {
            abortUnauthorized(requestContext, "Invalid token");
        }
    }

    /** Constant-time comparison to avoid leaking the token via response timing. */
    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8),
            b.getBytes(StandardCharsets.UTF_8));
    }

    private void abortUnauthorized(ContainerRequestContext requestContext, String message) {
        requestContext.abortWith(
            Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse(message))
                .build()
        );
    }
}
