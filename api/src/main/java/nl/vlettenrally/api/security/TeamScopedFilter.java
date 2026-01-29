package nl.vlettenrally.api.security;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Provider;
import nl.vlettenrally.data.dto.common.ErrorResponse;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * JAX-RS filter that enforces team-scoped access control.
 * 
 * This filter is triggered by the {@link TeamScoped} annotation and verifies
 * that the authenticated team's ID matches the "teamId" path parameter.
 * 
 * Priority is set to AUTHORIZATION (2000) to run after authentication.
 */
@TeamScoped
@Provider
@Priority(Priorities.AUTHORIZATION)
public class TeamScopedFilter implements ContainerRequestFilter {

    private static final String TEAM_ID_PARAM = "teamId";

    @Override
    public void filter(ContainerRequestContext requestContext) {
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
            requestedTeamId = UUID.fromString(teamIdValues.get(0));
        } catch (IllegalArgumentException e) {
            abortWithForbidden(requestContext, "Access denied: invalid team identifier");
            return;
        }

        // Get authenticated team ID from security context
        Principal principal = requestContext.getSecurityContext().getUserPrincipal();
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
