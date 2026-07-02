package nl.vlettenrally.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.dto.game.RankingResponse;
import nl.vlettenrally.data.repository.TeamRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    @Mock TeamRepository teamRepository;
    @Mock PointsTotalsCache pointsTotalsCache;

    @InjectMocks RankingService rankingService;

    private final UUID gameId = UUID.randomUUID();

    private Team team(String name, int total, boolean checkedIn) {
        Team t = new Team();
        t.setId(UUID.randomUUID());
        t.setGameId(gameId);
        t.setName(name);
        t.setCheckedIn(checkedIn);
        lenient().when(pointsTotalsCache.get(t.getId())).thenReturn(total);
        return t;
    }

    @Test
    void buildRanking_sortsDescending_sharesTiedPositions_andSkipsAfterTie() {
        // Two teams tie at the top (share position 1); the next lower total is position 3,
        // not 2 — a tie consumes the positions it spans. This is the invariant the O(n)
        // single-pass rewrite must preserve.
        Team x = team("X", 100, true);
        Team a = team("A", 100, true);
        Team b = team("B", 50, true);
        Team unchecked = team("U", 999, false); // excluded regardless of total

        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(b, x, unchecked, a));

        List<RankingResponse> ranking = rankingService.buildRanking(gameId);

        assertEquals(3, ranking.size(), "unchecked team is excluded");
        // Sorted by points desc: the two 100s first, then the 50.
        assertEquals(100, ranking.get(0).points());
        assertEquals(100, ranking.get(1).points());
        assertEquals(50, ranking.get(2).points());
        // Tied leaders share position 1; the 50-point team is position 3.
        assertEquals(1, ranking.get(0).position());
        assertEquals(1, ranking.get(1).position());
        assertEquals(3, ranking.get(2).position());
    }

    @Test
    void buildRanking_distinctTotals_giveSequentialPositions() {
        Team x = team("X", 30, true);
        Team y = team("Y", 20, true);
        Team z = team("Z", 10, true);
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(z, x, y));

        List<RankingResponse> ranking = rankingService.buildRanking(gameId);

        assertEquals(List.of(1, 2, 3), ranking.stream().map(RankingResponse::position).toList());
        assertEquals(List.of(30, 20, 10), ranking.stream().map(RankingResponse::points).toList());
    }
}
