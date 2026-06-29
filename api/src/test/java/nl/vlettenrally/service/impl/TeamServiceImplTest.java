package nl.vlettenrally.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.eq;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.service.AutomationEngine;
import nl.vlettenrally.service.GameService;
import nl.vlettenrally.service.PictureService;
import nl.vlettenrally.websockets.WebSocketPublisher;

/**
 * Unit tests for TeamServiceImpl.
 * Tests team check-in and other team operations.
 */
@ExtendWith(MockitoExtension.class)
class TeamServiceImplTest {

    @Mock
    TeamRepository teamRepository;

    @Mock
    GameService gameService;

    @Mock
    WebSocketPublisher webSocketPublisher;

    @Mock
    PictureService pictureService;

    @Mock
    AutomationEngine automationEngine;

    @InjectMocks
    TeamServiceImpl teamService;

    private UUID teamId;
    private UUID gameId;

    @BeforeEach
    void setUp() {
        teamId = UUID.randomUUID();
        gameId = UUID.randomUUID();
        // maxRivals is @ConfigProperty so Mockito leaves it at 0; restore the default for tests.
        teamService.maxRivals = 5;
    }

    @Test
    void checkIn_successfulCheckIn_setsCheckedInTrue() {
        // Arrange
        Team team = createTeam(false);
        team.getEmergencyPhoneNumbers().clear();
        team.getEmergencyPhoneNumbers().add("+31612345678");

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Team result = teamService.checkIn(teamId);

        // Assert
        assertTrue(result.isCheckedIn());
        assertTrue(result.isCheckedInDigitally());
        assertNotNull(result.getUpdatedAt());

        ArgumentCaptor<Team> teamCaptor = ArgumentCaptor.forClass(Team.class);
        verify(teamRepository).save(teamCaptor.capture());
        assertTrue(teamCaptor.getValue().isCheckedIn());
        assertTrue(teamCaptor.getValue().isCheckedInDigitally());
    }

    @Test
    void checkIn_alreadyCheckedIn_throwsIllegalStateException() {
        // Arrange
        Team team = createTeam(true);
        team.getEmergencyPhoneNumbers().clear();
        team.getEmergencyPhoneNumbers().add("+31612345678");

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        // Act & Assert
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> teamService.checkIn(teamId)
        );

        assertEquals("Team is already checked in digitally", exception.getMessage());
        verify(teamRepository, never()).save(any(Team.class));
    }

    @Test
    void checkIn_manualCheckedIn_setsCheckedInDigitally() {
        // Arrange
        Team team = createTeam(true);
        team.setCheckedInDigitally(false);
        team.getEmergencyPhoneNumbers().clear();

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Team result = teamService.checkIn(teamId);

        // Assert
        assertTrue(result.isCheckedIn());
        assertTrue(result.isCheckedInDigitally());
        assertNotNull(result.getUpdatedAt());

        ArgumentCaptor<Team> teamCaptor = ArgumentCaptor.forClass(Team.class);
        verify(teamRepository).save(teamCaptor.capture());
        assertTrue(teamCaptor.getValue().isCheckedInDigitally());
    }

    @Test
    void checkIn_teamNotFound_throwsIllegalArgumentException() {
        // Arrange
        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> teamService.checkIn(teamId)
        );

        assertTrue(exception.getMessage().contains("Team not found"));
        verify(teamRepository, never()).save(any(Team.class));
    }

    @Test
    void checkIn_noEmergencyPhoneNumber_throwsIllegalArgumentException() {
        // Arrange
        Team team = createTeam(false);
        team.getEmergencyPhoneNumbers().clear(); // No emergency phone numbers

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> teamService.checkIn(teamId)
        );

        assertTrue(exception.getMessage().contains("emergency phone number"));
        verify(teamRepository, never()).save(any(Team.class));
    }

    @Test
    void checkIn_nullEmergencyPhoneNumbers_throwsIllegalArgumentException() {
        // Arrange
        Team team = new Team();
        team.setId(teamId);
        team.setGameId(gameId);
        team.setName("Test Team");
        team.setCheckedIn(false);
        // emergencyPhoneNumbers will be initialized to empty ArrayList by Team constructor

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> teamService.checkIn(teamId)
        );

        assertTrue(exception.getMessage().contains("emergency phone number"));
        verify(teamRepository, never()).save(any(Team.class));
    }

    @Test
    void checkIn_updatesTimestamp() {
        // Arrange
        Team team = createTeam(false);
        team.getEmergencyPhoneNumbers().clear();
        team.getEmergencyPhoneNumbers().add("+31612345678");
        Instant beforeCheckIn = Instant.now().minusSeconds(1);
        team.setUpdatedAt(beforeCheckIn);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Team result = teamService.checkIn(teamId);

        // Assert
        assertTrue(result.getUpdatedAt().isAfter(beforeCheckIn));
    }

    @Test
    void checkIn_triggersAutomationEngine() {
        Team team = createTeam(false);
        team.getEmergencyPhoneNumbers().add("+31612345678");

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> invocation.getArgument(0));

        teamService.checkIn(teamId);

        verify(automationEngine).onTeamCheckIn(eq(gameId), eq(teamId));
    }

    @Test
    void findById_existingTeam_returnsTeam() {
        // Arrange
        Team team = createTeam(false);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        // Act
        Optional<Team> result = teamService.findById(teamId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(teamId, result.get().getId());
    }

    @Test
    void findById_nonExistingTeam_returnsEmpty() {
        // Arrange
        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

        // Act
        Optional<Team> result = teamService.findById(teamId);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void findByGameId_returnsTeamsForGame() {
        // Arrange
        Team team1 = createTeam(false);
        Team team2 = createTeam(true);
        team2.setId(UUID.randomUUID());
        team2.setName("Team 2");

        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(team1, team2));

        // Act
        List<Team> result = teamService.findByGameId(gameId);

        // Assert
        assertEquals(2, result.size());
    }

    @Test
    void save_delegatesToRepository() {
        // Arrange
        Team team = createTeam(false);
        when(teamRepository.save(team)).thenReturn(team);

        // Act
        Team result = teamService.save(team);

        // Assert
        assertEquals(team, result);
        verify(teamRepository).save(team);
    }

    @Test
    void delete_delegatesToRepository() {
        // Act
        teamService.delete(teamId);

        // Assert
        verify(teamRepository).delete(teamId);
    }

    @Test
    void delete_alsoRemovesTeamPictures() {
        // Act
        teamService.delete(teamId);

        // Assert: picture objects are cleaned up before the team row is removed.
        verify(pictureService).deleteAllForTeam(teamId);
        verify(teamRepository).delete(teamId);
    }

    // ---- setRivals -------------------------------------------------------

    @Test
    void setRivals_validIds_persistsAndReturnsRivals() {
        Team self = createTeam(true);
        UUID rivalAId = UUID.randomUUID();
        UUID rivalBId = UUID.randomUUID();
        Team rivalA = makeTeamInGame(rivalAId, gameId, "Rival A");
        Team rivalB = makeTeamInGame(rivalBId, gameId, "Rival B");

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(self));
        when(gameService.findById(gameId)).thenReturn(Optional.of(makeGame(Game.GameStatus.ACTIVE)));
        when(teamRepository.findById(rivalAId)).thenReturn(Optional.of(rivalA));
        when(teamRepository.findById(rivalBId)).thenReturn(Optional.of(rivalB));
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));

        List<Team> result = teamService.setRivals(teamId, List.of(rivalAId, rivalBId));

        assertEquals(2, result.size());
        ArgumentCaptor<Team> captor = ArgumentCaptor.forClass(Team.class);
        verify(teamRepository).save(captor.capture());
        assertEquals(List.of(rivalAId, rivalBId), captor.getValue().getRivals());
    }

    @Test
    void setRivals_moreThanMax_throws() {
        Team self = createTeam(true);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(self));
        when(gameService.findById(gameId)).thenReturn(Optional.of(makeGame(Game.GameStatus.ACTIVE)));

        List<UUID> sixIds = List.of(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> teamService.setRivals(teamId, sixIds));
        verify(teamRepository, never()).save(any(Team.class));
    }

    @Test
    void setRivals_selfRival_throws() {
        Team self = createTeam(true);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(self));
        when(gameService.findById(gameId)).thenReturn(Optional.of(makeGame(Game.GameStatus.ACTIVE)));

        assertThrows(IllegalArgumentException.class,
            () -> teamService.setRivals(teamId, List.of(teamId)));
        verify(teamRepository, never()).save(any(Team.class));
    }

    @Test
    void setRivals_rivalFromOtherGame_throws() {
        Team self = createTeam(true);
        UUID otherGameRivalId = UUID.randomUUID();
        Team otherGameRival = makeTeamInGame(otherGameRivalId, UUID.randomUUID(), "Other");

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(self));
        when(gameService.findById(gameId)).thenReturn(Optional.of(makeGame(Game.GameStatus.ACTIVE)));
        when(teamRepository.findById(otherGameRivalId)).thenReturn(Optional.of(otherGameRival));

        assertThrows(IllegalArgumentException.class,
            () -> teamService.setRivals(teamId, List.of(otherGameRivalId)));
        verify(teamRepository, never()).save(any(Team.class));
    }

    @Test
    void setRivals_completedGame_throws() {
        Team self = createTeam(true);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(self));
        when(gameService.findById(gameId)).thenReturn(Optional.of(makeGame(Game.GameStatus.COMPLETED)));

        assertThrows(IllegalStateException.class,
            () -> teamService.setRivals(teamId, List.of(UUID.randomUUID())));
        verify(teamRepository, never()).save(any(Team.class));
    }

    private Team makeTeamInGame(UUID id, UUID gId, String name) {
        Team team = new Team();
        team.setId(id);
        team.setGameId(gId);
        team.setName(name);
        team.setRivals(new ArrayList<>());
        team.setCreatedAt(Instant.now());
        team.setUpdatedAt(Instant.now());
        return team;
    }

    private Game makeGame(Game.GameStatus status) {
        Game g = new Game();
        g.setId(gameId);
        g.setStatus(status);
        return g;
    }

    private Team createTeam(boolean checkedIn) {
        Team team = new Team();
        team.setId(teamId);
        team.setGameId(gameId);
        team.setName("Test Team");
        team.setDescription("Test Description");
        team.setCheckedIn(checkedIn);
        team.setCheckedInDigitally(checkedIn);
        team.setTotalPoints(0);
        team.setParticipants(new ArrayList<>());
        team.setRivals(new ArrayList<>());
        team.setCreatedAt(Instant.now());
        team.setUpdatedAt(Instant.now());
        return team;
    }
}

