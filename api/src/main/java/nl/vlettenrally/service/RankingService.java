package nl.vlettenrally.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.dto.game.RankingResponse;
import nl.vlettenrally.data.repository.TeamRepository;

import java.util.ArrayList;
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
        return buildRanking(gameId, Map.of());
    }

    /**
     * As {@link #buildRanking(UUID)}, but a team's total is taken from
     * {@code totalOverrides} when present instead of from {@link PointsTotalsCache}.
     * A points change broadcast supplies the just-computed total for the changed
     * team(s) here, so the ranking reflects it regardless of whether the separate
     * post-commit cache write has run yet — see the rival-payload note in
     * {@link PointsLedgerService}.
     */
    public List<RankingResponse> buildRanking(UUID gameId, Map<UUID, Integer> totalOverrides) {
        List<Team> checkedIn = teamRepository.findByGameId(gameId).stream()
            .filter(Team::isCheckedIn)
            .toList();

        Map<UUID, Integer> totals = checkedIn.stream()
            .collect(Collectors.toMap(Team::getId,
                t -> totalOverrides.getOrDefault(t.getId(), pointsTotalsCache.get(t.getId()))));

        List<Team> sorted = checkedIn.stream()
            .sorted(Comparator.comparingInt((Team t) -> totals.get(t.getId())).reversed())
            .toList();

        // Single pass over the sorted list: position only advances when the total
        // drops below the previous team's, so ties share a position (1-based). This
        // is the same result as the previous per-team inner scan, but O(n) instead
        // of O(n²) — it runs on every ranking build and every published-standings broadcast.
        List<RankingResponse> ranking = new ArrayList<>(sorted.size());
        int position = 0;
        int rank = 0;
        Integer previousTotal = null;
        for (Team team : sorted) {
            int teamTotal = totals.get(team.getId());
            position++;
            if (previousTotal == null || teamTotal < previousTotal) {
                rank = position;
                previousTotal = teamTotal;
            }
            ranking.add(new RankingResponse(rank, team.getId(), team.getName(), team.getDescription(), teamTotal));
        }
        return ranking;
    }
}
