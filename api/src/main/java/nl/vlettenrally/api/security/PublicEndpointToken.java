package nl.vlettenrally.api.security;

import jakarta.ws.rs.NameBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Security annotation guarding the public-endpoint API (gallery, scoreboard, …) with a single
 * shared secret.
 * <p>
 * Endpoints annotated with this require an {@code X-Public-Endpoint-Token: <token>} header that
 * matches the configured {@code vlettenrally.public-endpoint.token}. The legacy
 * {@code X-Public-Gallery-Token} header is still accepted for backward compatibility. This is
 * intentionally separate from the JWT/team authentication: it lets an external, embeddable consumer
 * (e.g. an event slideshow or scoreboard) read public data without a team or admin login. A
 * dedicated header (rather than {@code Authorization: Bearer}) is used so the shared secret is never
 * seen by the SmallRye JWT mechanism, which would otherwise reject it as an invalid JWT.
 * <p>
 * If the token is not configured, or the header is missing or wrong, a 401 Unauthorized response
 * is returned (fail closed). See {@link PublicEndpointTokenFilter}.
 */
@NameBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface PublicEndpointToken {
}
