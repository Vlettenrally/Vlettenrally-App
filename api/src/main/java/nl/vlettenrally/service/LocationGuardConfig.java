package nl.vlettenrally.service;

import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.service.util.TrackOutlierFilter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.UUID;

/**
 * Resolves and caches the location-ingest outlier guards for a game.
 * <p>
 * Each {@link Game} may override any guard; an unset (null) override inherits the global default
 * from {@code application.properties} ({@code vlettenrally.location.*}). The resolved
 * {@link TrackOutlierFilter.Thresholds} are read on every ingested fix, so they are memoized
 * per replica (Caffeine, short TTL configured via
 * {@code quarkus.cache.caffeine."location-guards".expire-after-write}).
 * <p>
 * It must be a separate bean from {@link LocationIngestService}: {@link CacheResult} is applied by
 * a CDI interceptor, which only fires when the method is invoked through the proxy (from another
 * bean), not via self-invocation. {@link #invalidate(UUID)} clears the editing replica's entry
 * immediately after an admin edits a game; other replicas converge within the TTL.
 */
@ApplicationScoped
public class LocationGuardConfig {

    @Inject
    GameService gameService;

    @ConfigProperty(name = "vlettenrally.location.min-interval-seconds", defaultValue = "5")
    long minIntervalSeconds;

    @ConfigProperty(name = "vlettenrally.location.max-speed-kmh", defaultValue = "120")
    double maxSpeedKmh;

    @ConfigProperty(name = "vlettenrally.location.boat-max-speed-kmh", defaultValue = "25")
    double boatMaxSpeedKmh;

    @ConfigProperty(name = "vlettenrally.location.turn-angle-threshold-degrees", defaultValue = "120")
    double turnAngleThresholdDegrees;

    @ConfigProperty(name = "vlettenrally.location.min-bearing-segment-meters", defaultValue = "10")
    double minBearingSegmentMeters;

    @ConfigProperty(name = "vlettenrally.location.max-accuracy-meters", defaultValue = "50")
    double maxAccuracyMeters;

    @ConfigProperty(name = "vlettenrally.location.stale-fix-seconds", defaultValue = "300")
    long staleFixSeconds;

    /**
     * The resolved guard thresholds for {@code gameId}: each value is the game's override when set,
     * otherwise the global default. An unknown game (or all-null overrides) yields the global
     * defaults. Cached per replica for the configured TTL.
     */
    @CacheResult(cacheName = "location-guards")
    public TrackOutlierFilter.Thresholds thresholdsFor(UUID gameId) {
        Game g = gameService.findById(gameId).orElse(null);
        return new TrackOutlierFilter.Thresholds(
            g != null && g.getLocMinIntervalSeconds() != null ? g.getLocMinIntervalSeconds() : minIntervalSeconds,
            g != null && g.getLocMaxAccuracyMeters() != null ? g.getLocMaxAccuracyMeters() : maxAccuracyMeters,
            g != null && g.getLocMaxSpeedKmh() != null ? g.getLocMaxSpeedKmh() : maxSpeedKmh,
            g != null && g.getLocBoatMaxSpeedKmh() != null ? g.getLocBoatMaxSpeedKmh() : boatMaxSpeedKmh,
            g != null && g.getLocTurnAngleThresholdDegrees() != null ? g.getLocTurnAngleThresholdDegrees() : turnAngleThresholdDegrees,
            g != null && g.getLocMinBearingSegmentMeters() != null ? g.getLocMinBearingSegmentMeters() : minBearingSegmentMeters,
            g != null && g.getLocStaleFixSeconds() != null ? g.getLocStaleFixSeconds() : staleFixSeconds);
    }

    /** Evict the cached thresholds for a game, e.g. after an admin edits its guard config. */
    @CacheInvalidate(cacheName = "location-guards")
    public void invalidate(UUID gameId) {
        // Body intentionally empty: the interceptor performs the eviction.
    }
}
