package nl.vlettenrally.api.v1;

import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.dto.game.RankingResponse;
import nl.vlettenrally.service.GameService;
import nl.vlettenrally.service.RankingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link PublicRankingCache} assembly logic. The {@code @CacheResult} memoization
 * itself is provided by the Quarkus cache interceptor (configured via
 * {@code quarkus.cache.caffeine."public-latest-ranking".expire-after-write}); here we only verify
 * the latest-published selection and ranking delegation.
 */
@ExtendWith(MockitoExtension.class)
class PublicRankingCacheTest {

    @Mock GameService gameService;
    @Mock RankingService rankingService;

    @InjectMocks PublicRankingCache cache;

    @Test
    void latestPublishedRanking_picksMostRecentPublishedGame() {
        Game oldPublished = game("Spring", Instant.parse("2026-04-01T10:00:00Z"), true);
        Game newPublished = game("Summer", Instant.parse("2026-06-01T10:00:00Z"), true);
        Game newestUnpublished = game("Autumn", Instant.parse("2026-09-01T10:00:00Z"), false);
        when(gameService.findAll()).thenReturn(List.of(oldPublished, newPublished, newestUnpublished));
        RankingResponse entry = new RankingResponse(1, UUID.randomUUID(), "De Watervlooien", "Snelle en wendbare vlettenvaarders", 42, false, null);
        when(rankingService.buildRanking(newPublished.getId())).thenReturn(List.of(entry));

        List<RankingResponse> result = cache.latestPublishedRanking();

        assertEquals(1, result.size());
        assertEquals(42, result.getFirst().points());
    }

    @Test
    void latestPublishedRanking_emptyWhenNonePublished() {
        Game unpublished = game("Spring", Instant.parse("2026-04-01T10:00:00Z"), false);
        when(gameService.findAll()).thenReturn(List.of(unpublished));

        List<RankingResponse> result = cache.latestPublishedRanking();

        assertTrue(result.isEmpty());
    }

    private Game game(String name, Instant startTime, boolean rankingsPublished) {
        Game g = new Game();
        g.setId(UUID.randomUUID());
        g.setName(name);
        g.setStartTime(startTime);
        g.setRankingsPublished(rankingsPublished);
        return g;
    }
}
