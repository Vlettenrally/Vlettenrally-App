package nl.vlettenrally.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.Optional;

/**
 * Configuration for the external pre-registration API (WordPress)
 */
@ConfigMapping(prefix = "vlettenrally.pre-registration")
public interface PreRegistrationConfig {
    
    /**
     * Base URL for the pre-registration API
     */
    @WithName("base-url")
    @WithDefault("https://vlettenrally.nl/wp-json/vr/v1")
    String baseUrl();
    
    /**
     * Optional API key for authentication
     * If set, will be sent as Authorization header
     */
    @WithName("api-key")
    Optional<String> apiKey();
}
