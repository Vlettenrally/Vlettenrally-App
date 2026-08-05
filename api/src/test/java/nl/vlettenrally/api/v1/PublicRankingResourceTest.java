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

import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PublicRankingResource}: the public scoreboard endpoints and their
 * empty-list-until-published gate.
 */
@ExtendWith(MockitoExtension.class)
class PublicRankingResourceTest {

    @Mock GameService gameService;
    @Mock RankingService rankingService;
    @Mock PublicRankingCache rankingCache;

    @InjectMocks PublicRankingResource resource;

    @Test
    void rankingsForGame_returnsEmptyWhenNotPublished() {
        UUID gameId = UUID.randomUUID();
        Game g = game();
        g.setRankingsPublished(false);
        when(gameService.findById(gameId)).thenReturn(Optional.of(g));

        Response response = resource.rankingsForGame(gameId);

        assertEquals(200, response.getStatus());
        assertTrue(((List<?>) response.getEntity()).isEmpty());
        verify(rankingService, never()).buildRanking(gameId);
    }

    @Test
    void rankingsForGame_returnsEmptyWhenGameMissing() {
        UUID gameId = UUID.randomUUID();
        when(gameService.findById(gameId)).thenReturn(Optional.empty());

        Response response = resource.rankingsForGame(gameId);

        assertEquals(200, response.getStatus());
        assertTrue(((List<?>) response.getEntity()).isEmpty());
        verify(rankingService, never()).buildRanking(gameId);
    }

    @Test
    void rankingsForGame_returnsLeaderboardWhenPublished() {
        UUID gameId = UUID.randomUUID();
        Game g = game();
        g.setRankingsPublished(true);
        RankingResponse entry = new RankingResponse(1, UUID.randomUUID(), "De Watervlooien", "Snelle en wendbare vlettenvaarders", 42, false, null);
        when(gameService.findById(gameId)).thenReturn(Optional.of(g));
        when(rankingService.buildRanking(gameId)).thenReturn(List.of(entry));

        Response response = resource.rankingsForGame(gameId);

        assertEquals(200, response.getStatus());
        List<?> body = assertInstanceOf(List.class, response.getEntity());
        assertEquals(1, body.size());
        assertEquals(42, ((RankingResponse) body.getFirst()).points());
    }

    @Test
    void rankingsForLatestGame_returnsCachedRanking() {
        RankingResponse entry = new RankingResponse(1, UUID.randomUUID(), "De Watervlooien", "Snelle en wendbare vlettenvaarders", 42, false, null);
        when(rankingCache.latestPublishedRanking()).thenReturn(List.of(entry));

        Response response = resource.rankingsForLatestGame();

        assertEquals(200, response.getStatus());
        assertEquals(1, ((List<?>) response.getEntity()).size());
    }

    private Game game() {
        Game g = new Game();
        g.setName("Spring Rally");
        g.setStartTime(Instant.parse("2026-04-01T10:00:00Z"));
        return g;
    }
}
