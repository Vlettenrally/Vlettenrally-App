package nl.vlettenrally.api.v1;

import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.dto.game.RankingResponse;
import nl.vlettenrally.service.GameService;
import nl.vlettenrally.service.RankingService;

import java.util.Comparator;
import java.util.List;

/**
 * Caches the assembled "latest published ranking" response.
 * <p>
 * Mirrors {@link PublicGalleryCache}: the {@code /api/v1/public/games/latest/rankings} endpoint
 * takes no parameters, so it is the hottest public ranking route and rebuilds a full team scan plus
 * sort on every hit. This bean memoizes the finished DTO list with a short TTL (configured via
 * {@code quarkus.cache.caffeine."public-latest-ranking".expire-after-write}).
 * <p>
 * It must be a separate bean from {@link PublicRankingResource}: {@link CacheResult} is applied by a
 * CDI interceptor, which only fires when the method is invoked through the proxy (i.e. from another
 * bean), not via self-invocation. A short staleness window after an admin publishes is acceptable.
 */
@ApplicationScoped
public class PublicRankingCache {

    @Inject
    GameService gameService;

    @Inject
    RankingService rankingService;

    /**
     * The most recent game (by start time) whose rankings have been published, as a public
     * leaderboard, cached for the configured TTL. Empty when no game has published rankings.
     */
    @CacheResult(cacheName = "public-latest-ranking")
    public List<RankingResponse> latestPublishedRanking() {
        return gameService.findAll().stream()
            .filter(g -> Boolean.TRUE.equals(g.getPublicRankingsEnabled()) || Boolean.TRUE.equals(g.getRankingsPublished()))
            .max(Comparator.comparing(Game::getStartTime,
                Comparator.nullsLast(Comparator.naturalOrder())))
            .map(g -> rankingService.buildRanking(g.getId()))
            .orElse(List.of());
    }
}
