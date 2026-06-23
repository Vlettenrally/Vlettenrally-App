package nl.vlettenrally.config;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;

import java.util.Optional;

/**
 * Runtime-resolved {@link PublicGalleryConfig}.
 * <p>
 * The value is read <em>fresh</em> from the injected {@link Config} on every call, rather than from
 * a cached {@code @ConfigProperty} field. In a native image a {@code @ConfigProperty} value can be
 * frozen at build time (when {@code PUBLIC_GALLERY_TOKEN} is unset), so a runtime lookup is what
 * guarantees the Kubernetes environment variable is honoured. {@code Config.getOptionalValue} reads
 * the live runtime config sources (including environment variables) each time.
 */
@ApplicationScoped
public class PublicGalleryConfigImpl implements PublicGalleryConfig {

    static final String TOKEN_PROPERTY = "vlettenrally.public-gallery.token";

    @Inject
    Config config;

    @Override
    public Optional<String> token() {
        return config.getOptionalValue(TOKEN_PROPERTY, String.class);
    }

    /**
     * Log at startup whether the secret actually propagated, without ever printing it. This is the
     * fastest way to confirm in Kubernetes whether the native runtime picked up the env var.
     */
    void onStart(@Observes StartupEvent event) {
        String resolved = token().map(String::trim).filter(t -> !t.isEmpty()).orElse(null);
        if (resolved != null) {
            Log.infof("Public gallery API ENABLED: %s resolved at runtime (length=%d).",
                TOKEN_PROPERTY, resolved.length());
        } else {
            Log.warnf("Public gallery API DISABLED: %s is empty/unset at runtime. "
                + "Set the PUBLIC_GALLERY_TOKEN environment variable on the pod.", TOKEN_PROPERTY);
        }
    }
}
