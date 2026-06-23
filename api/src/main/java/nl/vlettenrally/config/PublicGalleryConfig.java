package nl.vlettenrally.config;

import java.util.Optional;

/**
 * Configuration for the public gallery API.
 * <p>
 * Deliberately a plain interface backed by a normal {@link jakarta.enterprise.context.ApplicationScoped}
 * bean ({@link PublicGalleryConfigImpl}), <em>not</em> a {@code @ConfigMapping} or a
 * {@code @ConfigProperty} field on the security filter. The filter is a JAX-RS {@code @Provider}
 * that Quarkus instantiates during static initialization; resolving the secret directly there would
 * either capture the build-time value (empty) or fail to find a synthetic config-mapping bean. By
 * injecting this normal-scoped bean the filter holds a client proxy and the value is read lazily at
 * request time, reflecting the real runtime secret.
 */
public interface PublicGalleryConfig {

    /**
     * Shared secret guarding the public gallery API. Clients pass it as
     * {@code Authorization: Bearer <token>}. Empty/unset disables the API entirely (fail closed).
     */
    Optional<String> token();
}
