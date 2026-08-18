package nl.vlettenrally.api.v1;

import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.dto.game.TeamLocationResponse;
import nl.vlettenrally.service.LocationService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Memoizes the latest-position-per-team payload behind the authenticated admin map.
 * <p>
 * The public narrowcasting map has had {@link PublicMapCache} for this since it is polled by
 * several displays at once; the staff dashboard had no equivalent, so every open browser paid
 * the full query on load and on every {@code useAdminResync}. The TTL is very short (see
 * {@code quarkus.cache.caffeine."admin-map-locations".expire-after-write}) because positions
 * are live — after the initial fetch the map is kept current by {@code LOCATION_UPDATE}
 * websocket events, so this exists only to collapse concurrent loads, not to serve stale maps.
 * <p>
 * It must be a separate bean from {@link LocationResource}: {@link CacheResult} is applied by a
 * CDI interceptor, which only fires when the method is invoked through the proxy (i.e. from
 * another bean), not via self-invocation. Same reasoning as {@link PublicMapCache}.
 */
@ApplicationScoped
public class AdminMapLocationsCache {

    @Inject
    LocationService locationService;

    /**
     * Latest location per team for a game, or empty when the game does not exist.
     */
    @CacheResult(cacheName = "admin-map-locations")
    public Optional<List<TeamLocationResponse>> latestFor(UUID gameId) {
        return locationService.getLatestTeamLocations(gameId);
    }
}
