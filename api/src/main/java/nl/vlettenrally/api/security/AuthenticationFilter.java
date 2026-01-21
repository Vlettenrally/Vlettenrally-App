package nl.vlettenrally.api.security;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.service.AuthService;

import java.security.Principal;
import java.util.Optional;
import java.util.UUID;

@Secured
@Provider
@Priority(Priorities.AUTHENTICATION)
public class AuthenticationFilter implements ContainerRequestFilter {

    @Inject
    private AuthService authService;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String authHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        String token = extractToken(authHeader);

        if (token == null) {
            abortWithUnauthorized(requestContext, "Missing or invalid authorization token");
            return;
        }

        Optional<UUID> teamIdOpt = authService.validateAccessToken(token);

        if (teamIdOpt.isEmpty()) {
            abortWithUnauthorized(requestContext, "Invalid or expired access token");
            return;
        }

        UUID teamId = teamIdOpt.get();
        requestContext.setSecurityContext(new TeamSecurityContext(teamId));
    }

    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    private void abortWithUnauthorized(ContainerRequestContext requestContext, String message) {
        requestContext.abortWith(
            Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse(message))
                .build()
        );
    }

    private static class TeamSecurityContext implements SecurityContext {
        private final UUID teamId;

        public TeamSecurityContext(UUID teamId) {
            this.teamId = teamId;
        }

        @Override
        public Principal getUserPrincipal() {
            return () -> teamId.toString();
        }

        @Override
        public boolean isUserInRole(String role) {
            return true;
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public String getAuthenticationScheme() {
            return "Bearer";
        }

        public UUID getTeamId() {
            return teamId;
        }
    }
}
