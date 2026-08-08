package nl.vlettenrally.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.dto.game.RankingResponse;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.util.Csv;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    @Mock TeamRepository teamRepository;
    @Mock PointsTotalsCache pointsTotalsCache;
    @Mock GameService gameService;

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

    @Test
    void buildRanking_overrideTakesPrecedenceOverCache_forTotalAndPosition() {
        // Z is last per the cache (10). An override lifts it to 40 — it must sort to the top
        // (points 40, position 1) even though the cache still reports 10. This is the
        // ordering-independence a points-change broadcast relies on.
        Team x = team("X", 30, true);
        Team y = team("Y", 20, true);
        Team z = team("Z", 10, true);
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(z, x, y));

        List<RankingResponse> ranking = rankingService.buildRanking(gameId, Map.of(z.getId(), 40));

        assertEquals(z.getId(), ranking.getFirst().teamId());
        assertEquals(40, ranking.getFirst().points(), "override total is used, not the cached 10");
        assertEquals(1, ranking.getFirst().position(), "override moves the team to the top");
        // The non-overridden teams keep their cached totals and shift down.
        assertEquals(List.of(40, 30, 20), ranking.stream().map(RankingResponse::points).toList());
        assertEquals(List.of(1, 2, 3), ranking.stream().map(RankingResponse::position).toList());
    }

    // ---- CSV export ---------------------------------------------------------

    @Test
    void exportCsv_writesBomHeaderAndSemicolonSeparatedRows() {
        Team x = team("Boot 1", 30, true);
        x.setDescription("Scouting Alpha");
        x.setNotes("Snelle start");
        Team y = team("Boot 2", 20, true);
        y.setDescription("Scouting Beta");
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(y, x));

        String csv = rankingService.exportCsv(gameId);

        assertEquals(Csv.UTF8_BOM + """
            plaats;bootnummer;groep;notities;punten;finish
            1;Boot 1;Scouting Alpha;Snelle start;30;
            2;Boot 2;Scouting Beta;;20;
            """, csv, "a team without notes gets an empty cell, not \"null\"");
    }

    @Test
    void exportCsv_tiedTeamsShareTheirPlace() {
        Team x = team("Boot 1", 100, true);
        Team a = team("Boot 2", 100, true);
        Team b = team("Boot 3", 50, true);
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(b, x, a));

        List<String> places = placesOf(rankingService.exportCsv(gameId));

        // Same invariant as buildRanking: a tie consumes the positions it spans.
        assertEquals(List.of("1", "1", "3"), places);
    }

    @Test
    void exportCsv_appendsTeamsThatNeverCheckedIn_withoutAPlace() {
        Team ranked = team("Boot 1", 30, true);
        Team noShowB = team("Boot 3", 0, false);
        Team noShowA = team("Boot 2", 0, false);
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(noShowB, ranked, noShowA));

        String csv = rankingService.exportCsv(gameId);

        // No-shows are excluded from the ranking itself but belong in the record: they follow
        // the ranked rows, sorted by boat number, with an empty place cell.
        assertEquals(List.of("1", "", ""), placesOf(csv));
        assertEquals(List.of("Boot 1", "Boot 2", "Boot 3"), boatsOf(csv));
    }

    @Test
    void exportCsv_quotesCellsContainingTheDelimiterQuotesOrNewlines() {
        Team x = team("Boot 1", 30, true);
        x.setDescription("Scouting \"De Vletten\"");
        x.setNotes("Te laat; twee keer\ngestrand");
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(x));

        String csv = rankingService.exportCsv(gameId);

        assertEquals(Csv.UTF8_BOM + """
            plaats;bootnummer;groep;notities;punten;finish
            1;Boot 1;"Scouting ""De Vletten""\";"Te laat; twee keer
            gestrand";30;
            """, csv);
    }

    @Test
    void buildRanking_carriesFinishedFlagAndTimestamp_withoutAffectingOrder() {
        Team late = team("Laat", 100, true);
        late.setFinished(true);
        late.setFinishedAt(Instant.parse("2026-08-05T18:30:00Z"));
        Team out = team("Onderweg", 50, true);

        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(out, late));

        List<RankingResponse> ranking = rankingService.buildRanking(gameId);

        // A late finish is a marker only: order still follows points.
        assertEquals(List.of("Laat", "Onderweg"), ranking.stream().map(RankingResponse::teamName).toList());
        assertEquals(List.of(1, 2), ranking.stream().map(RankingResponse::position).toList());
        assertTrue(ranking.getFirst().finished());
        assertEquals("2026-08-05T18:30:00Z", ranking.getFirst().finishedAt());
        assertFalse(ranking.get(1).finished());
        assertNull(ranking.get(1).finishedAt(), "an unfinished team carries no timestamp");
    }

    @Test
    void buildRanking_sinksDisqualifiedTeamsToTheBottom_withoutARankPosition() {
        Team dsq = team("DSQ team", 999, true); // highest points, but disqualified
        dsq.setDisqualified(true);
        Team high = team("High", 30, true);
        Team low = team("Low", 10, true);
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(dsq, low, high));

        List<RankingResponse> ranking = rankingService.buildRanking(gameId);

        assertEquals(List.of("High", "Low", "DSQ team"),
            ranking.stream().map(RankingResponse::teamName).toList());
        assertEquals(List.of(1, 2, 0), ranking.stream().map(RankingResponse::position).toList(),
            "disqualified team gets position 0 rather than a rank, and does not consume one");
        assertEquals(List.of(false, false, true), ranking.stream().map(RankingResponse::disqualified).toList());
    }

    @Test
    void buildRanking_ordersMultipleDisqualifiedTeamsByPointsAmongThemselves() {
        Team dsqHigh = team("DSQ High", 50, true);
        dsqHigh.setDisqualified(true);
        Team dsqLow = team("DSQ Low", 10, true);
        dsqLow.setDisqualified(true);
        Team ranked = team("Ranked", 30, true);
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(dsqLow, ranked, dsqHigh));

        List<RankingResponse> ranking = rankingService.buildRanking(gameId);

        assertEquals(List.of("Ranked", "DSQ High", "DSQ Low"),
            ranking.stream().map(RankingResponse::teamName).toList());
    }

    @Test
    void exportCsv_showsDsqInPlaceAndFinishColumnsForDisqualifiedTeams() {
        Team dsq = team("Boot 1", 40, true);
        dsq.setDisqualified(true);
        Team ranked = team("Boot 2", 20, true);
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(dsq, ranked));

        String csv = rankingService.exportCsv(gameId);

        assertEquals(List.of("1", "DSQ"), placesOf(csv));
        assertEquals(List.of("", "gediskwalificeerd"), columnOf(csv, 5));
    }

    @Test
    void exportCsv_labelsFinishStateAgainstTheGameEndTime() {
        Game game = new Game();
        game.setEndTime(Instant.parse("2026-08-05T18:00:00Z"));
        game.setStatus(Game.GameStatus.COMPLETED);
        when(gameService.findById(gameId)).thenReturn(Optional.of(game));

        Team onTime = team("Boot 1", 40, true);
        onTime.setFinished(true);
        onTime.setFinishedAt(Instant.parse("2026-08-05T17:59:00Z"));
        Team late = team("Boot 2", 30, true);
        late.setFinished(true);
        late.setFinishedAt(Instant.parse("2026-08-05T18:20:00Z"));
        Team neverCameIn = team("Boot 3", 20, true);
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(onTime, late, neverCameIn));

        List<String> finish = columnOf(rankingService.exportCsv(gameId), 5);

        assertEquals(List.of("gefinisht", "te laat", "dnf"), finish);
    }

    @Test
    void exportCsv_leavesFinishBlankWhileTheGameIsStillRunning() {
        Game game = new Game();
        game.setEndTime(Instant.parse("2026-08-05T18:00:00Z"));
        game.setStatus(Game.GameStatus.ACTIVE);
        when(gameService.findById(gameId)).thenReturn(Optional.of(game));

        Team stillOut = team("Boot 1", 20, true);
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(stillOut));

        assertEquals(List.of(""), columnOf(rankingService.exportCsv(gameId), 5),
            "a team still on the water during a running game is not DNF yet");
    }

    /** The first cell of every row after the header. */
    private List<String> placesOf(String csv) {
        return columnOf(csv, 0);
    }

    /** The boat-number cell of every row after the header. */
    private List<String> boatsOf(String csv) {
        return columnOf(csv, 1);
    }

    private List<String> columnOf(String csv, int index) {
        return csv.lines().skip(1)
            .map(line -> line.split(";", -1)[index])
            .toList();
    }
}
