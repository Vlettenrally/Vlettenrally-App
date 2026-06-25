package nl.vlettenrally.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.data.repository.TrackerPoolRepository;

/**
 * Unit tests for TrackerAssignmentService: a pasted batch of tracker IDs is
 * auto-distributed across teams without a tracker, and the mapping is exported.
 */
@ExtendWith(MockitoExtension.class)
class TrackerAssignmentServiceTest {

    @Mock
    TeamRepository teamRepository;

    @Mock
    TrackerPoolRepository trackerPoolRepository;

    @InjectMocks
    TrackerAssignmentService service;

    private UUID gameId;

    @BeforeEach
    void setUp() {
        gameId = UUID.randomUUID();
    }

    @Test
    void assignTrackers_distributesToFreeTeams() {
        Team a = team("7", null);
        Team b = team("12", null);
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(a, b));
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));

        TrackerAssignmentResult result = service.assignTrackers(gameId, List.of("T1", "T2"));

        assertEquals(2, result.assigned());
        assertEquals(0, result.teamsWithoutTracker());
        assertEquals(0, result.unused());
        assertEquals("T1", a.getTrackerId());
        assertEquals("T2", b.getTrackerId());
        assertEquals(List.of("T1 -> 7", "T2 -> 12"), result.assignedPairs());
        verify(teamRepository, times(2)).save(any(Team.class));
    }

    @Test
    void assignTrackers_skipsTeamsThatAlreadyHaveTracker_andDropsUsedIds() {
        Team assigned = team("7", "T1");
        Team free = team("12", null);
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(assigned, free));
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));

        // Re-paste T1 (already in use) plus a new T2: T1 dropped, T2 goes to the free team.
        TrackerAssignmentResult result = service.assignTrackers(gameId, List.of("T1", "T2"));

        assertEquals(1, result.assigned());
        assertEquals(0, result.unused());
        assertEquals("T1", assigned.getTrackerId());
        assertEquals("T2", free.getTrackerId());
        verify(teamRepository, times(1)).save(any(Team.class));
    }

    @Test
    void assignTrackers_dedupesAndTrimsInput() {
        Team free = team("7", null);
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(free));
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));

        TrackerAssignmentResult result = service.assignTrackers(gameId, List.of(" T1 ", "T1", "", "  "));

        assertEquals(1, result.assigned());
        assertEquals("T1", free.getTrackerId());
        assertEquals(0, result.unused());
    }

    @Test
    void assignTrackers_moreTrackersThanTeams_reportsUnused() {
        Team free = team("7", null);
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(free));
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));

        TrackerAssignmentResult result = service.assignTrackers(gameId, List.of("T1", "T2", "T3"));

        assertEquals(1, result.assigned());
        assertEquals(2, result.unused());
        assertEquals(List.of("T2", "T3"), result.unusedTrackers());
        verify(teamRepository, times(1)).save(any(Team.class));
    }

    @Test
    void assignTrackers_moreTeamsThanTrackers_reportsTeamsWithoutTracker() {
        Team a = team("7", null);
        Team b = team("12", null);
        Team c = team("103", null);
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(a, b, c));
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));

        TrackerAssignmentResult result = service.assignTrackers(gameId, List.of("T1"));

        assertEquals(1, result.assigned());
        assertEquals(2, result.teamsWithoutTracker());
        assertEquals(List.of("12", "103"), result.teamsWithoutTrackerNames());
    }

    @Test
    void assignTrackers_emptyInput_isNoOp() {
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(team("7", null)));

        TrackerAssignmentResult result = service.assignTrackers(gameId, List.of());

        assertEquals(0, result.assigned());
        assertEquals(1, result.teamsWithoutTracker());
        verify(teamRepository, never()).save(any(Team.class));
    }

    @Test
    void exportCsv_returnsAssignedRows_withHeaderAndEscaping() {
        Team a = team("7", "T1");
        a.setDescription("Scouting Delft");
        Team b = team("12", "T2");
        b.setDescription("Scouting, Gouda"); // comma forces quoting
        Team unassigned = team("103", null);
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(a, b, unassigned));

        String csv = service.exportCsv(gameId);

        assertEquals(
            "trackerId,boatNumber,scoutingGroup\n"
                + "T1,7,Scouting Delft\n"
                + "T2,12,\"Scouting, Gouda\"\n",
            csv);
    }

    @Test
    void assignTrackers_persistsPastedIdsToPool() {
        Team free = team("7", null);
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(free));
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));

        service.assignTrackers(gameId, List.of("T1", "T2"));

        // Both pasted IDs are stored in the pool, even though only one could be assigned.
        verify(trackerPoolRepository).addAll(gameId, Set.of("T1", "T2"));
    }

    @Test
    void availableTrackers_returnsPoolMinusAssigned_sorted() {
        Team assigned = team("7", "T2");
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(assigned, team("12", null)));
        when(trackerPoolRepository.findTrackerIdsByGameId(gameId)).thenReturn(Set.of("T3", "T1", "T2"));

        assertEquals(List.of("T1", "T3"), service.availableTrackers(gameId));
    }

    @Test
    void assignToTeam_assignsTrackerFromPool() {
        Team team = team("7", null);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(team));
        when(trackerPoolRepository.findTrackerIdsByGameId(gameId)).thenReturn(Set.of("T1"));
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));

        Team result = service.assignToTeam(team.getId(), "T1");

        assertEquals("T1", result.getTrackerId());
    }

    @Test
    void assignToTeam_clearsTrackerWhenBlank() {
        Team team = team("7", "T1");
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));

        Team result = service.assignToTeam(team.getId(), "  ");

        assertNull(result.getTrackerId());
    }

    @Test
    void assignToTeam_rejectsTrackerNotInPool() {
        Team team = team("7", null);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(team));
        when(trackerPoolRepository.findTrackerIdsByGameId(gameId)).thenReturn(Set.of());

        assertThrows(IllegalArgumentException.class, () -> service.assignToTeam(team.getId(), "T9"));
        verify(teamRepository, never()).save(any(Team.class));
    }

    @Test
    void assignToTeam_rejectsTrackerAssignedToAnotherTeam() {
        Team target = team("7", null);
        Team other = team("12", "T1");
        when(teamRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(target, other));

        assertThrows(IllegalStateException.class, () -> service.assignToTeam(target.getId(), "T1"));
        verify(teamRepository, never()).save(any(Team.class));
    }

    @Test
    void assignToTeam_throwsWhenTeamNotFound() {
        UUID missing = UUID.randomUUID();
        when(teamRepository.findById(missing)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.assignToTeam(missing, "T1"));
    }

    private Team team(String boatNumber, String trackerId) {
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setGameId(gameId);
        team.setName(boatNumber);
        team.setTrackerId(trackerId);
        return team;
    }
}
