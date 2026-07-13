package nl.vlettenrally.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
 * pool, listing trackers decorated with their current assignment, and toggling
 * the enabled flag.
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
