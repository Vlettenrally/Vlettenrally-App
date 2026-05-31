package nl.vlettenrally.api.security;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.service.AuthService;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.Instant;
import java.util.UUID;

/**
 * Rejects team access tokens whose `iat` predates the team's revocation cutoff
 * (set by {@link AuthService#revokeTokens(java.util.UUID)} on admin check-in reset).
 *
 * Runs after Quarkus authentication (Priorities.AUTHENTICATION + 1) and only for
 * requests with the "team" role, so admin tokens are unaffected.
 */
@Provider
@Priority(Priorities.AUTHENTICATION + 1)
public class TeamTokenRevocationFilter implements ContainerRequestFilter {

    @Inject
    JsonWebToken jwt;

    @Inject
    AuthService authService;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        SecurityContext securityContext = requestContext.getSecurityContext();
        if (securityContext == null || securityContext.getUserPrincipal() == null) {
            return;
        }
        if (!securityContext.isUserInRole("team")) {
            return;
        }

        // JWT may be empty/anonymous if the request is unauthenticated — guard.
        if (jwt == null || jwt.getRawToken() == null) {
            return;
        }

        long iatEpoch = jwt.getIssuedAtTime();
        if (iatEpoch <= 0) {
            return;
        }

        String subject = jwt.getSubject();
        if (subject == null) {
            return;
        }

        Instant issuedAt = Instant.ofEpochSecond(iatEpoch);
        if (authService.isAccessTokenRevoked(subject, issuedAt)) {
            requestContext.abortWith(
                Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ErrorResponse("Team check-in is reset — please log in again"))
                    .build()
            );
            return;
        }

        // Per-device revocation (admin removed this specific device from the team).
        Object didClaim = jwt.getClaim("did");
        if (didClaim != null) {
            try {
                UUID deviceId = UUID.fromString(didClaim.toString());
                if (authService.isDeviceTokenRevoked(subject, deviceId, issuedAt)) {
                    requestContext.abortWith(
                        Response.status(Response.Status.UNAUTHORIZED)
                            .entity(new ErrorResponse("This device has been removed from the team — please log in again"))
                            .build()
                    );
                }
            } catch (IllegalArgumentException ignored) {
                // Malformed did claim — let the request through; the standard auth filter
                // will catch other inconsistencies.
            }
        }
    }
}
