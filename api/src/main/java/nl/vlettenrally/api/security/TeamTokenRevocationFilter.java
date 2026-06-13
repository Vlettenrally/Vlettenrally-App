package nl.vlettenrally.api.security;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.service.AuthService;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

import java.time.Instant;
import java.util.UUID;

/**
 * Rejects team access tokens whose `iat` predates the team's revocation cutoff
 * (set by {@link AuthService#revokeTokens(java.util.UUID)} on admin check-in reset).
 *
 * Runs after Quarkus authentication (Priorities.AUTHENTICATION + 1) and only for
 * requests with the "team" role, so admin tokens are unaffected.
 *
 * Implemented as a reactive {@code @ServerRequestFilter} returning {@code Uni<Response>}:
 * the cutoff lookup hits Valkey, and on a non-blocking endpoint this filter runs on the
 * Vert.x event loop, where a blocking call would throw. Returning a non-null Response
 * aborts the request; a null item lets it continue.
 */
@ApplicationScoped
public class TeamTokenRevocationFilter {

    @Inject
    JsonWebToken jwt;

    @Inject
    AuthService authService;

    @ServerRequestFilter(priority = Priorities.AUTHENTICATION + 1)
    public Uni<Response> filter(ContainerRequestContext requestContext) {
        SecurityContext securityContext = requestContext.getSecurityContext();
        if (securityContext == null || securityContext.getUserPrincipal() == null) {
            return Uni.createFrom().nullItem();
        }
        if (!securityContext.isUserInRole("team")) {
            return Uni.createFrom().nullItem();
        }

        // JWT may be empty/anonymous if the request is unauthenticated — guard.
        if (jwt == null || jwt.getRawToken() == null) {
            return Uni.createFrom().nullItem();
        }

        long iatEpoch = jwt.getIssuedAtTime();
        if (iatEpoch <= 0) {
            return Uni.createFrom().nullItem();
        }

        String subject = jwt.getSubject();
        if (subject == null) {
            return Uni.createFrom().nullItem();
        }

        Instant issuedAt = Instant.ofEpochSecond(iatEpoch);
        UUID deviceId = parseDeviceId(jwt.getClaim("did"));

        return authService.isAccessTokenRevoked(subject, issuedAt)
            .flatMap(subjectRevoked -> {
                if (Boolean.TRUE.equals(subjectRevoked)) {
                    return Uni.createFrom().item(
                        unauthorized("Team check-in is reset — please log in again"));
                }
                // Per-device revocation (admin removed this specific device from the team).
                if (deviceId == null) {
                    return Uni.createFrom().nullItem();
                }
                return authService.isDeviceTokenRevoked(subject, deviceId, issuedAt)
                    .map(deviceRevoked -> Boolean.TRUE.equals(deviceRevoked)
                        ? unauthorized("This device has been removed from the team — please log in again")
                        : null);
            });
    }

    private static UUID parseDeviceId(Object didClaim) {
        if (didClaim == null) {
            return null;
        }
        try {
            return UUID.fromString(didClaim.toString());
        } catch (IllegalArgumentException ignored) {
            // Malformed did claim — let the request through; the standard auth filter
            // will catch other inconsistencies.
            return null;
        }
    }

    private static Response unauthorized(String message) {
        return Response.status(Response.Status.UNAUTHORIZED)
            .entity(new ErrorResponse(message))
            .build();
    }
}
