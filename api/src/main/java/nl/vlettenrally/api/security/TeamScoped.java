package nl.vlettenrally.api.security;

import jakarta.ws.rs.NameBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Security annotation that verifies the authenticated team matches the {teamId} path parameter.
 * 
 * Use this annotation on endpoints where only the team itself should have access to its own resources.
 * The annotation expects a path parameter named "teamId" in the endpoint path.
 * 
 * Example:
 * <pre>
 * {@code
 * @GET
 * @Path("/teams/{teamId}/messages")
 * @TeamScoped
 * public Response getMessages(@PathParam("teamId") UUID teamId) { ... }
 * }
 * </pre>
 * 
 * If the authenticated team's ID does not match the requested teamId, a 403 Forbidden response is returned.
 */
@NameBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface TeamScoped {
}
