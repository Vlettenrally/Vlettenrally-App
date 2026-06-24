package nl.vlettenrally.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.dto.game.RankingResponse;
import nl.vlettenrally.data.repository.TeamRepository;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Builds the full, sorted ranking for a game.
 *
 * The full ranking exposes every team's score and is therefore only safe to
 * reveal once the game has COMPLETED. Callers are responsible for gating its
 * distribution on game status (see {@link PointsLedgerService} and
 * {@link GameStatusScheduler}).
 */
@ApplicationScoped
public class RankingService {

    @Inject
    TeamRepository teamRepository;

    /**
     * The checked-in teams of a game, sorted by total points descending, each
     * carrying its global (1-based, ties-share-position) ranking position.
     */
    public List<RankingResponse> buildRanking(UUID gameId) {
        List<Team> teams = teamRepository.findByGameId(gameId).stream()
            .filter(Team::isCheckedIn)
            .sorted(Comparator.comparingInt(Team::getTotalPoints).reversed())
            .toList();
        return teams.stream()
            .map(team -> {
                int position = 1;
                for (Team t : teams) {
                    if (t.getTotalPoints() > team.getTotalPoints()) {
                        position++;
                    }
                }
                return new RankingResponse(position, team.getId(), team.getName(), team.getDescription(), team.getTotalPoints());
            })
            .toList();
    }
}
