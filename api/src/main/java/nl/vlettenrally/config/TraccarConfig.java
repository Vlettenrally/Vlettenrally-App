package nl.vlettenrally.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Configuration for per-team Traccar tracking — the public URL participants
 * enter into the Traccar Client app, which then reports GPS straight to our
 * OsmAnd-compatible ingestion endpoint.
 */
@ConfigMapping(prefix = "vlettenrally.traccar")
public interface TraccarConfig {

    /**
     * The publicly reachable base URL of our ingestion endpoint, shown to
     * participants during check-in..
     */
    @WithName("public-url")
    @WithDefault("https://game.vlettenrally.nl/api/v1/traccar")
    String publicUrl();
}
