package nl.vlettenrally.api.security;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import nl.vlettenrally.config.PublicGalleryConfig;
import nl.vlettenrally.data.dto.common.ErrorResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * JAX-RS filter that guards {@link PublicGalleryToken}-annotated endpoints with a single shared
 * secret supplied in the {@value #TOKEN_HEADER} header.
 * <p>
 * A dedicated header is used rather than {@code Authorization: Bearer <token>} deliberately: the
 * app runs SmallRye JWT with proactive authentication, which eagerly tries to verify any
 * {@code Authorization: Bearer} value as a JWT and rejects the request with a bodyless 401 before
 * this filter ever runs. Keeping the shared secret in its own header sidesteps the JWT mechanism
 * entirely. See {@link PublicGalleryToken}.
 * <p>
 * Fails closed: if no token is configured the endpoint is effectively disabled. Runs at
 * AUTHENTICATION priority since it establishes whether the caller is allowed at all.
 */
@PublicGalleryToken
@Provider
@Priority(Priorities.AUTHENTICATION)
public class PublicGalleryTokenFilter implements ContainerRequestFilter {

    /** Header carrying the shared secret. Kept off {@code Authorization} to avoid the JWT mechanism. */
    static final String TOKEN_HEADER = "X-Public-Gallery-Token";

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

        String header = requestContext.getHeaderString(TOKEN_HEADER);
        if (header == null || header.isBlank()) {
            abortUnauthorized(requestContext, "Missing " + TOKEN_HEADER + " header");
            return;
        }

        String presented = header.trim();
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
