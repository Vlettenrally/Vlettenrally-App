package nl.vlettenrally.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.domain.Tracker;
import nl.vlettenrally.data.repository.GameRepository;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.data.repository.TrackerRepository;

/**
 * Unit tests for TrackerService: bulk-adding pasted tracker IDs to the global
 * pool, bulk-deleting them (unassigning any team that still holds one), listing
 * trackers decorated with their current assignment, and toggling the enabled flag.
 */
@ExtendWith(MockitoExtension.class)
class TrackerServiceTest {

    @Mock
    TrackerRepository trackerRepository;

    @Mock
    TeamRepository teamRepository;

    @Mock
    GameRepository gameRepository;

    @InjectMocks
    TrackerService service;

    @Test
    void addTrackers_trimsDedupesAndCountsDuplicates() {
        when(trackerRepository.addAll(anyCollection())).thenReturn(2);

        TrackerService.AddResult result = service.addTrackers(List.of(" T1 ", "T1", "T2", "T3", "", "  ", "T3"));

        // Four distinct IDs reach the repository; it reports two as new.
        verify(trackerRepository).addAll(new LinkedHashSet<>(List.of("T1", "T2", "T3")));
        assertEquals(2, result.added());
        assertEquals(1, result.duplicates());
    }

    @Test
    void addTrackers_nullInput_isNoOp() {
        when(trackerRepository.addAll(anyCollection())).thenReturn(0);

        TrackerService.AddResult result = service.addTrackers(null);

        assertEquals(0, result.added());
        assertEquals(0, result.duplicates());
    }

    @Test
    void listTrackers_decoratesWithAllAssignmentsAcrossGames() {
        // The same tracker may be assigned in multiple non-completed games at
        // once (the desk prepares the next game while the current one runs):
        // every holding team — game pair must be listed.
        Game active = game("Vlettenrally 2026", Game.GameStatus.ACTIVE);
        Game planned = game("Summer Edition", Game.GameStatus.PLANNED);

        when(gameRepository.findByStatus(Game.GameStatus.PLANNED)).thenReturn(List.of(planned));
        when(gameRepository.findByStatus(Game.GameStatus.ACTIVE)).thenReturn(List.of(active));
        when(teamRepository.findByGameId(active.getId())).thenReturn(List.of(team(active.getId(), "7", "T1")));
        when(teamRepository.findByGameId(planned.getId())).thenReturn(List.of(team(planned.getId(), "12", "T1")));
        when(trackerRepository.findAll()).thenReturn(List.of(tracker("T1"), tracker("T2")));

        List<TrackerService.TrackerWithAssignments> result = service.listTrackers();

        assertEquals(2, result.size());
        assertEquals(List.of(
                new TrackerService.Assignment("12", "Summer Edition"),
                new TrackerService.Assignment("7", "Vlettenrally 2026")),
                result.get(0).assignments());
        assertEquals(List.of(), result.get(1).assignments());
    }

    @Test
    void setEnabled_unknownTracker_returnsEmpty() {
        when(trackerRepository.setEnabled("T9", false)).thenReturn(Optional.empty());

        assertTrue(service.setEnabled("T9", false).isEmpty());
    }

    @Test
    void deleteTrackers_trimsDedupesAndCountsNotFound() {
        when(trackerRepository.deleteAll(anyCollection())).thenReturn(2);

        TrackerService.DeleteResult result = service.deleteTrackers(
                List.of(" T1 ", "T1", "T2", "T3", "", "  "));

        // Three distinct IDs reach the repository; only two were in the pool.
        verify(trackerRepository).deleteAll(new LinkedHashSet<>(List.of("T1", "T2", "T3")));
        assertEquals(2, result.deleted());
        assertEquals(1, result.notFound());
        assertEquals(List.of(), result.unassignedTeams());
    }

    @Test
    void deleteTrackers_emptyInput_isNoOp() {
        TrackerService.DeleteResult result = service.deleteTrackers(List.of("", "  "));

        verify(trackerRepository, never()).deleteAll(anyCollection());
        assertEquals(0, result.deleted());
        assertEquals(0, result.notFound());
    }

    @Test
    void deleteTrackers_unassignedTracker_leavesTeamsAlone() {
        Game active = game("Vlettenrally 2026", Game.GameStatus.ACTIVE);
        Team holder = team(active.getId(), "7", "T1");

        when(gameRepository.findByStatus(Game.GameStatus.PLANNED)).thenReturn(List.of());
        when(gameRepository.findByStatus(Game.GameStatus.ACTIVE)).thenReturn(List.of(active));
        when(teamRepository.findByGameId(active.getId())).thenReturn(List.of(holder));
        when(trackerRepository.deleteAll(anyCollection())).thenReturn(1);

        // T2 is in the pool but nobody holds it.
        TrackerService.DeleteResult result = service.deleteTrackers(List.of("T2"));

        verify(teamRepository, never()).save(any());
        assertEquals("T1", holder.getTrackerId());
        assertEquals(1, result.deleted());
        assertEquals(List.of(), result.unassignedTeams());
    }

    @Test
    void deleteTrackers_assignedTracker_unassignsTheTeamFirst() {
        // teams.tracker_id has no FK to the pool, so a team holding a deleted
        // tracker would be left pointing at a tracker that no longer exists.
        Game active = game("Vlettenrally 2026", Game.GameStatus.ACTIVE);
        Team holder = team(active.getId(), "7", "T1");
        holder.setTrackerAssignedAt(Instant.now());

        when(gameRepository.findByStatus(Game.GameStatus.PLANNED)).thenReturn(List.of());
        when(gameRepository.findByStatus(Game.GameStatus.ACTIVE)).thenReturn(List.of(active));
        when(teamRepository.findByGameId(active.getId())).thenReturn(List.of(holder));
        when(trackerRepository.deleteAll(anyCollection())).thenReturn(1);

        TrackerService.DeleteResult result = service.deleteTrackers(List.of("T1"));

        verify(teamRepository).save(holder);
        assertNull(holder.getTrackerId());
        assertNull(holder.getTrackerAssignedAt(), "the assignment stamp must not outlive the assignment");
        assertEquals(1, result.deleted());
        assertEquals(List.of("T1 -> 7 (Vlettenrally 2026)"), result.unassignedTeams());
    }

    @Test
    void deleteTrackers_completedGameHolder_keepsItsHistoricalTrackerId() {
        // A completed game's tracker IDs are the record of which tracker a boat
        // carried; deleting the pool entry must not rewrite that history.
        Game completed = game("Vlettenrally 2025", Game.GameStatus.COMPLETED);
        Team pastHolder = team(completed.getId(), "7", "T1");

        when(trackerRepository.deleteAll(anyCollection())).thenReturn(1);

        TrackerService.DeleteResult result = service.deleteTrackers(List.of("T1"));

        verify(gameRepository, never()).findByStatus(Game.GameStatus.COMPLETED);
        verify(teamRepository, never()).save(any());
        assertEquals("T1", pastHolder.getTrackerId());
        assertEquals(1, result.deleted());
        assertEquals(List.of(), result.unassignedTeams());
    }

    private static Game game(String name, Game.GameStatus status) {
        Game game = new Game();
        game.setId(UUID.randomUUID());
        game.setName(name);
        game.setStatus(status);
        return game;
    }

    private static Team team(UUID gameId, String boatNumber, String trackerId) {
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setGameId(gameId);
        team.setName(boatNumber);
        team.setTrackerId(trackerId);
        return team;
    }

    private static Tracker tracker(String trackerId) {
        Tracker tracker = new Tracker();
        tracker.setId(UUID.randomUUID());
        tracker.setTrackerId(trackerId);
        return tracker;
    }
}
