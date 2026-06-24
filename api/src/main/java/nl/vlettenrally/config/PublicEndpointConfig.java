package nl.vlettenrally.config;

import java.util.Optional;

/**
 * Configuration for the public-endpoint API (gallery, scoreboard, …).
 * <p>
 * Deliberately a plain interface backed by a normal {@link jakarta.enterprise.context.ApplicationScoped}
 * bean ({@link PublicEndpointConfigImpl}), <em>not</em> a {@code @ConfigMapping} or a
 * {@code @ConfigProperty} field on the security filter. The filter is a JAX-RS {@code @Provider}
 * that Quarkus instantiates during static initialization; resolving the secret directly there would
 * either capture the build-time value (empty) or fail to find a synthetic config-mapping bean. By
 * injecting this normal-scoped bean the filter holds a client proxy and the value is read lazily at
 * request time, reflecting the real runtime secret.
 */
public interface PublicEndpointConfig {

    /**
     * Shared secret guarding the public-endpoint API. Clients pass it in the
     * {@code X-Public-Endpoint-Token} header (or the legacy {@code X-Public-Gallery-Token}).
     * Empty/unset disables the API entirely (fail closed).
     */
    Optional<String> token();
}
