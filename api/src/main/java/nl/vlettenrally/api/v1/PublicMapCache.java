package nl.vlettenrally.api.v1;

import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.dto.game.PublicMapResponse;
import nl.vlettenrally.data.dto.game.PublicMapZoneResponse;
import nl.vlettenrally.data.dto.game.TeamLocationResponse;
import nl.vlettenrally.service.CheckpointService;
import nl.vlettenrally.service.GameService;
import nl.vlettenrally.service.LocationService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Assembles and caches the public narrowcasting map payload for a game.
 * <p>
 * The cost worth memoizing is {@link LocationService#getLatestTeamLocations}, which runs one query
 * per team; several displays polling the same game would otherwise multiply that by the number of
 * screens in the room. The TTL is short (see
 * {@code quarkus.cache.caffeine."public-map".expire-after-write}) because the payload carries live
 * positions — it exists to collapse concurrent polls, not to serve stale maps.
 * <p>
 * It must be a separate bean from {@link PublicMapResource}: {@link CacheResult} is applied by a CDI
 * interceptor, which only fires when the method is invoked through the proxy (i.e. from another
 * bean), not via self-invocation. Same reasoning as {@link PublicRankingCache}.
 */
@ApplicationScoped
public class PublicMapCache {

    @Inject
    GameService gameService;

    @Inject
    LocationService locationService;

    @Inject
    CheckpointService checkpointService;

    /**
     * The map payload for a game, or empty when the game does not exist.
     * <p>
     * Hidden zones are filtered out here. They drive automations but must never appear on a public
     * display — and unlike the authenticated {@code GET /games/{id}/checkpoints}, which serves both
     * staff and teams from one method, there is no caller of this path that should ever see them.
     */
    @CacheResult(cacheName = "public-map")
    public Optional<PublicMapResponse> mapFor(UUID gameId) {
        return gameService.findById(gameId).map(game -> {
            List<TeamLocationResponse> teams = locationService.getLatestTeamLocations(gameId)
                .orElseGet(List::of);

            List<PublicMapZoneResponse> zones = checkpointService.findByGameId(gameId).stream()
                .filter(cp -> !cp.isHidden())
                .map(PublicMapZoneResponse::from)
                .toList();

            return new PublicMapResponse(game.getId(), game.getName(), teams, zones);
        });
    }
}
