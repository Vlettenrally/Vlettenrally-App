package nl.vlettenrally.service;

import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.PauseWindows;
import nl.vlettenrally.data.repository.GamePausePeriodRepository;

import java.util.UUID;

/**
 * Resolves and caches a game's emergency-stop windows.
 * <p>
 * The windows gate distance accrual, so they are read on every ingested fix and on every
 * segment a rebuild walks. Memoized per replica (Caffeine, TTL configured via
 * {@code quarkus.cache.caffeine."game-pause-windows".expire-after-write}) alongside the
 * game itself — almost every game has zero windows and the repository read would otherwise
 * be pure overhead on the hottest path in the system.
 * <p>
 * It is a separate bean from {@link GamePauseService} for the same reason
 * {@link LocationGuardConfig} is separate from {@link LocationIngestService}:
 * {@link CacheResult} and {@link CacheInvalidate} are applied by a CDI interceptor that only
 * fires when the method is called through the proxy, i.e. from another bean. A pause service
 * that invalidated its own cache would silently keep serving the stale windows until the TTL
 * expired.
 */
@ApplicationScoped
public class GamePauseWindowCache {

    @Inject
    GamePausePeriodRepository pausePeriodRepository;

    /**
     * The game's pause windows, oldest first. Callers must take one snapshot per operation
     * and reuse it — see {@link PauseWindows} for why a rebuild that re-reads mid-walk can
     * produce a chain nothing else reproduces.
     */
    @CacheResult(cacheName = "game-pause-windows")
    public PauseWindows windowsFor(UUID gameId) {
        if (gameId == null) {
            return PauseWindows.none();
        }
        return new PauseWindows(pausePeriodRepository.findByGameId(gameId));
    }

    /**
     * Evict a game's cached windows. Called after a pause or resume commits; other replicas
     * converge within the TTL, which is why that TTL is kept to seconds.
     */
    @CacheInvalidate(cacheName = "game-pause-windows")
    public void invalidate(UUID gameId) {
        // Body intentionally empty: the interceptor performs the eviction.
    }
}
