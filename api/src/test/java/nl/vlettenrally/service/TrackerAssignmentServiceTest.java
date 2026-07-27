package nl.vlettenrally.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
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
import nl.vlettenrally.data.domain.Tracker;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.data.repository.TrackerRepository;

/**
 * Unit tests for TrackerAssignmentService: enabled trackers from the global
 * pool are auto-distributed across teams without a tracker. Uniqueness is per
 * game — the same tracker may be assigned in another game at the same time
 * (the desk prepares the next game while the current one runs) — and the
 * mapping is exported.
 */
@ExtendWith(MockitoExtension.class)
class TrackerAssignmentServiceTest {

    @Mock
    TeamRepository teamRepository;

    @Mock
    TrackerRepository trackerRepository;

    @InjectMocks
    TrackerAssignmentService service;

    private UUID gameId;

    @BeforeEach
    void setUp() {
        gameId = UUID.randomUUID();
    }

    @Test
    void assignTrackers_distributesEnabledPoolToFreeTeams() {
        Team a = team("7", null);
        Team b = team("12", null);
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(a, b));
        when(trackerRepository.findEnabledTrackerIds()).thenReturn(Set.of("T1", "T2"));
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));

        Instant before = Instant.now();
        TrackerAssignmentResult result = service.assignTrackers(gameId);
        Instant after = Instant.now();

        assertEquals(2, result.assigned());
        assertEquals(0, result.teamsWithoutTracker());
        assertEquals(0, result.unused());
        assertEquals("T1", a.getTrackerId());
        assertEquals("T2", b.getTrackerId());
        assertStampedBetween(a.getTrackerAssignedAt(), before, after);
        assertStampedBetween(b.getTrackerAssignedAt(), before, after);
        assertEquals(List.of("T1 -> 7", "T2 -> 12"), result.assignedPairs());
        verify(teamRepository, times(2)).save(any(Team.class));
    }

    @Test
    void assignTrackers_skipsTeamsThatAlreadyHaveTracker_andExcludesUsedIds() {
        Team assigned = team("7", "T1");
        Team free = team("12", null);
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(assigned, free));
        when(trackerRepository.findEnabledTrackerIds()).thenReturn(Set.of("T1", "T2"));
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));

        // T1 is already in use in this game: only T2 is handed out.
        TrackerAssignmentResult result = service.assignTrackers(gameId);

        assertEquals(1, result.assigned());
        assertEquals(0, result.unused());
        assertEquals("T1", assigned.getTrackerId());
        assertEquals("T2", free.getTrackerId());
        verify(teamRepository, times(1)).save(any(Team.class));
    }

    @Test
    void assignTrackers_trackerHeldInAnotherGameIsStillAssignable() {
        // Uniqueness is per game: only THIS game's teams are consulted, so a
        // tracker held by a team in another (e.g. still active) game is handed
        // out again when preparing this one.
        Team free = team("7", null);
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(free));
        when(trackerRepository.findEnabledTrackerIds()).thenReturn(Set.of("T1"));
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));

        TrackerAssignmentResult result = service.assignTrackers(gameId);

        assertEquals(1, result.assigned());
        assertEquals("T1", free.getTrackerId());
    }

    @Test
    void assignTrackers_moreTrackersThanTeams_reportsUnused() {
        Team free = team("7", null);
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(free));
        when(trackerRepository.findEnabledTrackerIds()).thenReturn(Set.of("T1", "T2", "T3"));
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));

        TrackerAssignmentResult result = service.assignTrackers(gameId);

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
        when(trackerRepository.findEnabledTrackerIds()).thenReturn(Set.of("T1"));
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));

        TrackerAssignmentResult result = service.assignTrackers(gameId);

        assertEquals(1, result.assigned());
        assertEquals(2, result.teamsWithoutTracker());
        assertEquals(List.of("12", "103"), result.teamsWithoutTrackerNames());
    }

    @Test
    void assignTrackers_emptyPool_isNoOp() {
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(team("7", null)));
        when(trackerRepository.findEnabledTrackerIds()).thenReturn(Set.of());

        TrackerAssignmentResult result = service.assignTrackers(gameId);

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
                """
                        trackerId,boatNumber,scoutingGroup
                        T1,7,Scouting Delft
                        T2,12,"Scouting, Gouda"
                        """,
            csv);
    }

    @Test
    void exportCsv_honoursSelectedColumns_theirOrderAndRenamedHeaders() {
        Team a = team("7", "T1");
        a.setDescription("Scouting Delft");
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(a));

        String csv = service.exportCsv(gameId, List.of(
                new TrackerAssignmentService.ExportColumn(TrackerAssignmentService.ExportField.NAME, "Boot"),
                new TrackerAssignmentService.ExportColumn(TrackerAssignmentService.ExportField.TRACKER_ID, "Tracker")),
            false);

        assertEquals(
                """
                        Boot,Tracker
                        7,T1
                        """,
            csv);
    }

    @Test
    void exportCsv_escapesRenamedHeaders() {
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of());

        String csv = service.exportCsv(gameId, List.of(
                new TrackerAssignmentService.ExportColumn(TrackerAssignmentService.ExportField.NAME, "Boot, nr")),
            false);

        assertEquals("\"Boot, nr\"\n", csv);
    }

    @Test
    void exportCsv_includeUnassigned_emitsTeamsWithoutTracker() {
        Team assigned = team("7", "T1");
        assigned.setDescription("Scouting Delft");
        Team unassigned = team("103", null);
        unassigned.setDescription("Scouting Gouda");
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(assigned, unassigned));

        String csv = service.exportCsv(gameId, TrackerAssignmentService.DEFAULT_COLUMNS, true);

        assertEquals(
                """
                        trackerId,boatNumber,scoutingGroup
                        T1,7,Scouting Delft
                        ,103,Scouting Gouda
                        """,
            csv);
    }

    @Test
    void availableTrackers_returnsEnabledMinusThisGamesAssigned_sorted() {
        Team assigned = team("7", "T2");
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(assigned, team("12", null)));
        when(trackerRepository.findEnabledTrackerIds()).thenReturn(Set.of("T3", "T1", "T2"));

        assertEquals(List.of("T1", "T3"), service.availableTrackers(gameId));
    }

    @Test
    void assignToTeam_assignsEnabledTrackerFromPool() {
        Team team = team("7", null);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(team));
        when(trackerRepository.findByTrackerId("T1")).thenReturn(Optional.of(tracker("T1", true)));
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));

        Instant before = Instant.now();
        Team result = service.assignToTeam(team.getId(), "T1");
        Instant after = Instant.now();

        assertEquals("T1", result.getTrackerId());
        assertStampedBetween(result.getTrackerAssignedAt(), before, after);
    }

    @Test
    void assignToTeam_clearsTrackerWhenBlank() {
        Team team = team("7", "T1");
        team.setTrackerAssignedAt(Instant.now());
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));

        Team result = service.assignToTeam(team.getId(), "  ");

        assertNull(result.getTrackerId());
        assertNull(result.getTrackerAssignedAt());
    }

    @Test
    void assignToTeam_reassigningSameTrackerKeepsTimestamp() {
        Instant original = Instant.now().minusSeconds(3600);
        Team team = team("7", "T1");
        team.setTrackerAssignedAt(original);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(team));
        when(trackerRepository.findByTrackerId("T1")).thenReturn(Optional.of(tracker("T1", true)));
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));

        // Re-picking the tracker the team already holds is idempotent: it must not re-stamp,
        // or a later backfill would lose the team's earlier (legitimately-owned) track.
        Team result = service.assignToTeam(team.getId(), "T1");

        assertEquals("T1", result.getTrackerId());
        assertEquals(original, result.getTrackerAssignedAt());
    }

    @Test
    void assignToTeam_swappingToDifferentTrackerRestamps() {
        Instant original = Instant.now().minusSeconds(3600);
        Team team = team("7", "T1");
        team.setTrackerAssignedAt(original);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(team));
        when(trackerRepository.findByTrackerId("T2")).thenReturn(Optional.of(tracker("T2", true)));
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));

        Instant before = Instant.now();
        Team result = service.assignToTeam(team.getId(), "T2");
        Instant after = Instant.now();

        assertEquals("T2", result.getTrackerId());
        assertStampedBetween(result.getTrackerAssignedAt(), before, after);
    }

    @Test
    void assignToTeam_rejectsTrackerNotInPool() {
        Team team = team("7", null);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(team));
        when(trackerRepository.findByTrackerId("T9")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.assignToTeam(team.getId(), "T9"));
        verify(teamRepository, never()).save(any(Team.class));
    }

    @Test
    void assignToTeam_rejectsDisabledTracker() {
        Team team = team("7", null);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(team));
        when(trackerRepository.findByTrackerId("T1")).thenReturn(Optional.of(tracker("T1", false)));

        assertThrows(IllegalArgumentException.class, () -> service.assignToTeam(team.getId(), "T1"));
        verify(teamRepository, never()).save(any(Team.class));
    }

    @Test
    void assignToTeam_rejectsTrackerAssignedToAnotherTeamInSameGame() {
        Team target = team("7", null);
        Team other = team("12", "T1");
        when(teamRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(target, other));

        assertThrows(IllegalStateException.class, () -> service.assignToTeam(target.getId(), "T1"));
        verify(teamRepository, never()).save(any(Team.class));
    }

    @Test
    void assignToTeam_allowsTrackerHeldInAnotherGame() {
        // Only this game's teams are consulted for conflicts; a holder in a
        // different game does not block the assignment.
        Team target = team("7", null);
        when(teamRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(target));
        when(trackerRepository.findByTrackerId("T1")).thenReturn(Optional.of(tracker("T1", true)));
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));

        Team result = service.assignToTeam(target.getId(), "T1");

        assertEquals("T1", result.getTrackerId());
    }

    @Test
    void assignToTeam_throwsWhenTeamNotFound() {
        UUID missing = UUID.randomUUID();
        when(teamRepository.findById(missing)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.assignToTeam(missing, "T1"));
    }

    private static void assertStampedBetween(Instant stamp, Instant before, Instant after) {
        assertNotNull(stamp);
        assertFalse(stamp.isBefore(before));
        assertFalse(stamp.isAfter(after));
    }

    private Team team(String boatNumber, String trackerId) {
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setGameId(gameId);
        team.setName(boatNumber);
        team.setTrackerId(trackerId);
        return team;
    }

    private static Tracker tracker(String trackerId, boolean enabled) {
        Tracker tracker = new Tracker();
        tracker.setId(UUID.randomUUID());
        tracker.setTrackerId(trackerId);
        tracker.setEnabled(enabled);
        return tracker;
    }
}
