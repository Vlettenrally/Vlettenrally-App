package nl.vlettenrally.api.security;

import jakarta.ws.rs.NameBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Security annotation guarding the public narrowcasting map with a per-game derived token.
 * <p>
 * Endpoints annotated with this require an {@code X-Public-Map-Token: <token>} header matching the
 * token {@link PublicMapTokenService} derives for the {@code {gameId}} path parameter of the request.
 * It is the sibling of {@link PublicEndpointToken}, and differs in exactly one way: that annotation
 * accepts the shared secret itself (one token for every public endpoint), whereas this one accepts
 * only a value derived from it for one specific game. A narrowcasting URL therefore travels with a
 * token that unlocks nothing but that game's map.
 * <p>
 * A dedicated header rather than {@code Authorization: Bearer} for the same reason as its sibling:
 * SmallRye JWT with proactive authentication eagerly tries to verify any Bearer value and rejects the
 * request with a bodyless 401 before a JAX-RS filter ever runs.
 * <p>
 * If the secret is not configured, or the header is missing or wrong, a 401 Unauthorized is returned
 * (fail closed). See {@link PublicMapTokenFilter}.
 */
@NameBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface PublicMapToken {
}
