package nl.vlettenrally.api.security;

import jakarta.ws.rs.NameBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Security annotation that verifies the authenticated team matches the {teamId} path parameter,
 * OR allows access if the user has the "admin" role.
 * 
 * Use this annotation on endpoints where a team should have access to its own resources,
 * but admins should also be able to access any team's resources.
 * The annotation expects a path parameter named "teamId" in the endpoint path.
 * 
 * Example:
 * <pre>
 * {@code
 * @GET
 * @Path("/teams/{teamId}/details")
 * @TeamScopedOrAdmin
 * public Response getTeamDetails(@PathParam("teamId") UUID teamId) { ... }
 * }
 * </pre>
 * 
 * If the authenticated user is not an admin and the team ID does not match, 
 * a 403 Forbidden response is returned.
 */
@NameBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface TeamScopedOrAdmin {
}
