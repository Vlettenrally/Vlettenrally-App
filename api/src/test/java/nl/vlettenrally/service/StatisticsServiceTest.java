package nl.vlettenrally.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import nl.vlettenrally.data.domain.Checkpoint;
import nl.vlettenrally.data.domain.CircleCheckpoint;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.dto.game.GameStatisticsResponse;
import nl.vlettenrally.data.enums.PointEntryType;
import nl.vlettenrally.data.repository.CheckpointRepository;
import nl.vlettenrally.data.repository.PointEntryRepository;
import nl.vlettenrally.data.repository.TeamDistanceSegmentRepository;
import nl.vlettenrally.data.repository.TeamRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    @Mock TeamRepository teamRepository;
    @Mock TeamDistanceSegmentRepository teamDistanceSegmentRepository;
    @Mock CheckpointRepository checkpointRepository;
    @Mock PointEntryRepository pointEntryRepository;

    @InjectMocks StatisticsService statisticsService;

    private final UUID gameId = UUID.randomUUID();

    @BeforeEach
    void stubZeroedPointEntryDefaults() {
        // buildStatistics always queries all four of these; tests that only care about
        // some of them still need the others to resolve, so default everything to zero.
        lenient().when(checkpointRepository.findByGameId(gameId)).thenReturn(List.of());
        lenient().when(pointEntryRepository.countByGame(gameId, null, PointEntryType.CHECKPOINT_VERIFICATION))
                .thenReturn(0L);
        lenient().when(pointEntryRepository.countByGame(gameId, null, PointEntryType.EARLY_FINISH_BONUS))
                .thenReturn(0L);
        lenient().when(pointEntryRepository.sumCurrentAmountByGame(gameId, null)).thenReturn(0);
        lenient().when(pointEntryRepository.sumCurrentAmountByGame(gameId, PointEntryType.DISTANCE_BONUS))
                .thenReturn(0);
    }

    private Team team(boolean checkedIn, boolean disqualified) {
        Team t = new Team();
        t.setId(UUID.randomUUID());
        t.setGameId(gameId);
        t.setCheckedIn(checkedIn);
        t.setDisqualified(disqualified);
        return t;
    }

    @Test
    void buildStatistics_countsTeamsByCheckInAndDisqualificationStatus() {
        Team checkedIn = team(true, false);
        Team notCheckedIn = team(false, false);
        Team disqualified = team(true, true);
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(checkedIn, notCheckedIn, disqualified));

        GameStatisticsResponse stats = statisticsService.buildStatistics(gameId);

        assertEquals(3, stats.totalTeams());
        assertEquals(2, stats.checkedInTeams());
        assertEquals(1, stats.disqualifiedTeams());
    }

    @Test
    void buildStatistics_sumsAveragesAndBoundsDistancePerTeam() {
        Team a = team(true, false);
        Team b = team(true, false);
        Team c = team(true, false);
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(a, b, c));
        when(teamDistanceSegmentRepository.sumMetersByTeamId(a.getId())).thenReturn(1000.0);
        when(teamDistanceSegmentRepository.sumMetersByTeamId(b.getId())).thenReturn(3000.0);
        when(teamDistanceSegmentRepository.sumMetersByTeamId(c.getId())).thenReturn(500.0);

        GameStatisticsResponse stats = statisticsService.buildStatistics(gameId);

        assertEquals(4500.0, stats.totalDistanceMeters());
        assertEquals(1500.0, stats.averageDistanceMeters());
        assertEquals(500.0, stats.minDistanceMeters());
        assertEquals(3000.0, stats.maxDistanceMeters());
    }

    @Test
    void buildStatistics_returnsZeroDistanceFields_whenGameHasNoTeams() {
        // An empty DoubleSummaryStatistics reports +/-Infinity for min/max — must be
        // guarded rather than leak into the response.
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of());

        GameStatisticsResponse stats = statisticsService.buildStatistics(gameId);

        assertEquals(0, stats.totalTeams());
        assertEquals(0.0, stats.totalDistanceMeters());
        assertEquals(0.0, stats.averageDistanceMeters());
        assertEquals(0.0, stats.minDistanceMeters());
        assertEquals(0.0, stats.maxDistanceMeters());
    }

    @Test
    void buildStatistics_countsCheckpointsAndVerifications() {
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of());
        when(checkpointRepository.findByGameId(gameId)).thenReturn(
                List.of(checkpoint(), checkpoint(), checkpoint()));
        when(pointEntryRepository.countByGame(gameId, null, PointEntryType.CHECKPOINT_VERIFICATION))
                .thenReturn(7L);

        GameStatisticsResponse stats = statisticsService.buildStatistics(gameId);

        assertEquals(3, stats.totalCheckpoints());
        assertEquals(7, stats.checkpointVerifications());
    }

    @Test
    void buildStatistics_sumsPointsAndCountsEarlyFinishBonuses() {
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of());
        when(pointEntryRepository.sumCurrentAmountByGame(gameId, null)).thenReturn(918);
        when(pointEntryRepository.sumCurrentAmountByGame(gameId, PointEntryType.DISTANCE_BONUS)).thenReturn(18);
        when(pointEntryRepository.countByGame(gameId, null, PointEntryType.EARLY_FINISH_BONUS)).thenReturn(2L);

        GameStatisticsResponse stats = statisticsService.buildStatistics(gameId);

        assertEquals(918, stats.totalPoints());
        assertEquals(18, stats.distanceBonusPoints());
        assertEquals(2, stats.earlyFinishBonusCount());
    }

    private Checkpoint checkpoint() {
        return new CircleCheckpoint();
    }
}
