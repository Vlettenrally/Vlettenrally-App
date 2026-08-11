package nl.vlettenrally.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.dto.game.GameStatisticsResponse;
import nl.vlettenrally.data.enums.PointEntryType;
import nl.vlettenrally.data.repository.CheckpointRepository;
import nl.vlettenrally.data.repository.PointEntryRepository;
import nl.vlettenrally.data.repository.TeamDistanceSegmentRepository;
import nl.vlettenrally.data.repository.TeamRepository;

import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.UUID;

/**
 * Builds the game-wide statistics shown on the admin Statistics page: team
 * participation, distance travelled, checkpoint activity and points/bonus totals.
 * <p>
 * Distance is summed per team via {@link TeamDistanceSegmentRepository#sumMetersByTeamId},
 * looped over the game's teams — there is no by-game bulk method (the segment table has
 * no game_id column, and the mock has no team-to-game index), and rally team counts are
 * in the tens, so N small queries is the simpler, mock-compatible option. Each call reads
 * from the PRIMARY datasource, so this loop should not be reused for a collection that
 * could grow into the thousands.
 * <p>
 * Points totals here are a live sum over the ledger ({@link PointEntryRepository}), distinct
 * from the cached per-team totals {@link RankingService} reads via {@link PointsTotalsCache} —
 * both are correct, but the two can momentarily disagree during a burst of writes.
 */
@ApplicationScoped
public class StatisticsService {

    @Inject
    TeamRepository teamRepository;

    @Inject
    TeamDistanceSegmentRepository teamDistanceSegmentRepository;

    @Inject
    CheckpointRepository checkpointRepository;

    @Inject
    PointEntryRepository pointEntryRepository;

    public GameStatisticsResponse buildStatistics(UUID gameId) {
        List<Team> teams = teamRepository.findByGameId(gameId);
        int totalTeams = teams.size();
        int checkedInTeams = (int) teams.stream().filter(Team::isCheckedIn).count();
        int disqualifiedTeams = (int) teams.stream().filter(Team::isDisqualified).count();

        DoubleSummaryStatistics distance = teams.stream()
                .mapToDouble(t -> teamDistanceSegmentRepository.sumMetersByTeamId(t.getId()))
                .summaryStatistics();
        // An empty DoubleSummaryStatistics reports +/-Infinity for min/max — guard
        // explicitly rather than let that leak into the response.
        double totalDistance = totalTeams == 0 ? 0.0 : distance.getSum();
        double avgDistance = totalTeams == 0 ? 0.0 : distance.getAverage();
        double minDistance = totalTeams == 0 ? 0.0 : distance.getMin();
        double maxDistance = totalTeams == 0 ? 0.0 : distance.getMax();

        int totalCheckpoints = checkpointRepository.findByGameId(gameId).size();
        long checkpointVerifications =
                pointEntryRepository.countByGame(gameId, null, PointEntryType.CHECKPOINT_VERIFICATION);

        int totalPoints = pointEntryRepository.sumCurrentAmountByGame(gameId, null);
        int distanceBonusPoints =
                pointEntryRepository.sumCurrentAmountByGame(gameId, PointEntryType.DISTANCE_BONUS);
        long earlyFinishBonusCount =
                pointEntryRepository.countByGame(gameId, null, PointEntryType.EARLY_FINISH_BONUS);

        return new GameStatisticsResponse(totalTeams, checkedInTeams, disqualifiedTeams,
                totalDistance, avgDistance, minDistance, maxDistance,
                totalCheckpoints, checkpointVerifications,
                totalPoints, distanceBonusPoints, earlyFinishBonusCount);
    }
}
