package nl.vlettenrally.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.dto.game.RankingResponse;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.util.Csv;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    /** Excel reads a CSV in the Dutch locale as semicolon-separated. */
    private static final char DELIMITER = ';';

    @Inject
    TeamRepository teamRepository;

    @Inject
    GameService gameService;

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
            ranking.add(new RankingResponse(rank, team.getId(), team.getName(), team.getDescription(), teamTotal,
                team.isFinished(),
                team.getFinishedAt() != null ? team.getFinishedAt().toString() : null));
        }
        return ranking;
    }

    /**
     * The standings as a CSV file for the rally desk: place, boat number, scouting group,
     * notes, points and finish state.
     * <p>
     * Two things set this apart from what the ranking page and the WebSocket broadcast carry.
     * It includes the admin-only {@code notes} of each team, which is why it is built here
     * rather than in the browser and why {@link RankingResponse} is deliberately not widened
     * to hold it. And it lists <em>every</em> team, not only the checked-in ones the ranking
     * itself contains: no-shows are appended after the ranked rows without a place, so the
     * file doubles as the administrative record of the event.
     * <p>
     * Semicolon-separated and BOM-prefixed because this file is opened by hand in a
     * Dutch-locale Excel — see {@link Csv}.
     */
    public String exportCsv(UUID gameId) {
        List<Team> teams = teamRepository.findByGameId(gameId);
        Map<UUID, String> notes = teams.stream()
            .filter(t -> t.getNotes() != null)
            .collect(Collectors.toMap(Team::getId, Team::getNotes));

        StringBuilder sb = new StringBuilder(Csv.UTF8_BOM);
        Csv.appendRow(sb, DELIMITER, Stream.of("plaats", "bootnummer", "groep", "notities", "punten", "finish"));

        Game game = gameService.findById(gameId).orElse(null);
        for (RankingResponse row : buildRanking(gameId)) {
            Csv.appendRow(sb, DELIMITER, Stream.of(
                String.valueOf(row.position()),
                row.teamName(),
                row.teamDescription(),
                notes.get(row.teamId()),
                String.valueOf(row.points()),
                finishLabel(row, game)));
        }

        teams.stream()
            .filter(t -> !t.isCheckedIn())
            .sorted(Comparator.comparing(Team::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
            .forEach(t -> Csv.appendRow(sb, DELIMITER, Stream.of(
                "",
                t.getName(),
                t.getDescription(),
                t.getNotes(),
                String.valueOf(pointsTotalsCache.get(t.getId())),
                // Deliberately blank: a team that never checked in did not start, which is a
                // different thing from a DNF, and the empty place column already says so.
                "")));

        return sb.toString();
    }

    /**
     * The team's finish state for the CSV, matching the chip the ranking page shows: blank while
     * the game is still running and the team is out, "dnf" once it has ended without them coming
     * in, "te laat" for a finish after the end time and "gefinisht" for one before it.
     */
    private String finishLabel(RankingResponse row, Game game) {
        if (row.finished()) {
            if (game != null && game.getEndTime() != null && row.finishedAt() != null
                && java.time.Instant.parse(row.finishedAt()).isAfter(game.getEndTime())) {
                return "te laat";
            }
            return "gefinisht";
        }
        return game != null && game.getStatus() == Game.GameStatus.COMPLETED ? "dnf" : "";
    }
}
