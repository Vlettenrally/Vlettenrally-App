package nl.vlettenrally.api.security;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Provider;
import nl.vlettenrally.data.dto.common.ErrorResponse;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * JAX-RS filter that enforces team-scoped access control with admin bypass.
 * 
 * This filter is triggered by the {@link TeamScopedOrAdmin} annotation and verifies
 * that either:
 * - The authenticated user has the "admin" role, OR
 * - The authenticated team's ID matches the "teamId" path parameter
 * Priority is set to AUTHORIZATION (2000) to run after authentication.
 */
@TeamScopedOrAdmin
@Provider
@Priority(Priorities.AUTHORIZATION)
public class TeamScopedOrAdminFilter implements ContainerRequestFilter {

    private static final String TEAM_ID_PARAM = "teamId";
    private static final String ADMIN_ROLE = "admin";

    @Override
    public void filter(ContainerRequestContext requestContext) {
        SecurityContext securityContext = requestContext.getSecurityContext();

        // Allow admins to bypass team verification
        if (securityContext.isUserInRole(ADMIN_ROLE)) {
            return;
        }

        // Extract teamId from path parameters
        UriInfo uriInfo = requestContext.getUriInfo();
        List<String> teamIdValues = uriInfo.getPathParameters().get(TEAM_ID_PARAM);

        if (teamIdValues == null || teamIdValues.isEmpty()) {
            // No teamId in path - this is a configuration error
            abortWithForbidden(requestContext, "Access denied: missing team identifier");
            return;
        }

        UUID requestedTeamId;
        try {
            requestedTeamId = UUID.fromString(teamIdValues.getFirst());
        } catch (IllegalArgumentException e) {
            abortWithForbidden(requestContext, "Access denied: invalid team identifier");
            return;
        }

        // Get authenticated team ID from security context
        Principal principal = securityContext.getUserPrincipal();
        if (principal == null) {
            abortWithForbidden(requestContext, "Access denied: not authenticated");
            return;
        }

        UUID authenticatedTeamId;
        try {
            authenticatedTeamId = UUID.fromString(principal.getName());
        } catch (IllegalArgumentException e) {
            abortWithForbidden(requestContext, "Access denied: invalid authentication");
            return;
        }

        // Verify the authenticated team matches the requested team
        if (!authenticatedTeamId.equals(requestedTeamId)) {
            abortWithForbidden(requestContext, "Access denied: cannot access another team's resources");
        }
    }

    private void abortWithForbidden(ContainerRequestContext requestContext, String message) {
        requestContext.abortWith(
            Response.status(Response.Status.FORBIDDEN)
                .entity(new ErrorResponse(message))
                .build()
        );
    }
}
