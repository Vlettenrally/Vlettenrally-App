package nl.vlettenrally.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

/**
 * Runtime-resolved {@link PublicGalleryConfig}. Normal-scoped so the {@code @ConfigProperty} is
 * injected when the bean is first used (at request time), not during the static-init phase that
 * builds the JAX-RS filter chain.
 */
@ApplicationScoped
public class PublicGalleryConfigImpl implements PublicGalleryConfig {

    @ConfigProperty(name = "vlettenrally.public-gallery.token")
    Optional<String> token;

    @Override
    public Optional<String> token() {
        return token;
    }
}
