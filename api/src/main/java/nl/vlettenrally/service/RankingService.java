package nl.vlettenrally.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.dto.game.RankingResponse;
import nl.vlettenrally.data.repository.TeamRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds the full, sorted ranking for a game.
 * <p>
 * The full ranking exposes every team's score and is therefore only safe to
 * reveal once the game has COMPLETED. Callers are responsible for gating its
 * distribution on game status (see {@link PointsLedgerService} and
 * {@link GameStatusScheduler}).
 */
@ApplicationScoped
public class RankingService {

    @Inject
    TeamRepository teamRepository;

    @Inject
    PointsTotalsCache pointsTotalsCache;

    /**
     * The checked-in teams of a game, sorted by total points descending, each
     * carrying its global (1-based, ties-share-position) ranking position.
     */
    public List<RankingResponse> buildRanking(UUID gameId) {
        List<Team> checkedIn = teamRepository.findByGameId(gameId).stream()
            .filter(Team::isCheckedIn)
            .toList();

        Map<UUID, Integer> totals = checkedIn.stream()
            .collect(Collectors.toMap(Team::getId, t -> pointsTotalsCache.get(t.getId())));

        List<Team> sorted = checkedIn.stream()
            .sorted(Comparator.comparingInt((Team t) -> totals.get(t.getId())).reversed())
            .toList();

        return sorted.stream()
            .map(team -> {
                int teamTotal = totals.get(team.getId());
                int position = 1;
                for (Team t : sorted) {
                    if (totals.get(t.getId()) > teamTotal) {
                        position++;
                    }
                }
                return new RankingResponse(position, team.getId(), team.getName(), team.getDescription(), teamTotal);
            })
            .toList();
    }
}
