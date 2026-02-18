package nl.vlettenrally.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

import nl.vlettenrally.client.PreRegistrationApiClient;
import nl.vlettenrally.client.dto.Registration;
import nl.vlettenrally.client.exception.PreRegistrationApiException;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.repository.TeamRepository;

/**
 * Unit tests for TeamImportService.
 * Tests team import from external pre-registration API.
 */
@ExtendWith(MockitoExtension.class)
class TeamImportServiceTest {

    @Mock
    PreRegistrationApiClient preRegistrationClient;

    @Mock
    TeamRepository teamRepository;

    @InjectMocks
    TeamImportService teamImportService;

    private UUID gameId;
    private int externalEventId;

    @BeforeEach
    void setUp() {
        gameId = UUID.randomUUID();
        externalEventId = 123;
    }

    @Test
    void importTeams_createsNewTeams_whenNoExistingTeams() throws PreRegistrationApiException {
        // Arrange
        List<Registration> registrations = List.of(
            createRegistration(1, "Team Alpha", "Scouting Group A"),
            createRegistration(2, "Team Beta", "Scouting Group B")
        );

        when(preRegistrationClient.fetchRegistrations(externalEventId)).thenReturn(registrations);
        when(teamRepository.findByGameIdAndExternalId(eq(gameId), any())).thenReturn(Optional.empty());
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        ImportResult result = teamImportService.importTeams(gameId, externalEventId);

        // Assert
        assertEquals(2, result.created());
        assertEquals(0, result.updated());
        assertEquals(0, result.errors());
        verify(teamRepository, times(2)).save(any(Team.class));
    }

    @Test
    void importTeams_updatesExistingTeams_whenTeamsAlreadyExist() throws PreRegistrationApiException {
        // Arrange
        Registration registration = createRegistration(1, "Updated Team Name", "Updated Group");
        List<Registration> registrations = List.of(registration);

        Team existingTeam = new Team();
        existingTeam.setId(UUID.randomUUID());
        existingTeam.setGameId(gameId);
        existingTeam.setExternalId("1");
        existingTeam.setName("Old Name");
        existingTeam.setDescription("Old Group");

        when(preRegistrationClient.fetchRegistrations(externalEventId)).thenReturn(registrations);
        when(teamRepository.findByGameIdAndExternalId(gameId, "1")).thenReturn(Optional.of(existingTeam));
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        ImportResult result = teamImportService.importTeams(gameId, externalEventId);

        // Assert
        assertEquals(0, result.created());
        assertEquals(1, result.updated());
        assertEquals(0, result.errors());

        ArgumentCaptor<Team> teamCaptor = ArgumentCaptor.forClass(Team.class);
        verify(teamRepository).save(teamCaptor.capture());

        Team savedTeam = teamCaptor.getValue();
        assertEquals("Updated Team Name", savedTeam.getName());
        assertEquals("Updated Group", savedTeam.getDescription());
    }

    @Test
    void importTeams_mapsTeamNameFromBoatNumberField() throws PreRegistrationApiException {
        // Arrange - the external API's boat_number field is actually the team name
        Registration registration = createRegistration(1, "My Team Name", "My Scouting Group");
        List<Registration> registrations = List.of(registration);

        when(preRegistrationClient.fetchRegistrations(externalEventId)).thenReturn(registrations);
        when(teamRepository.findByGameIdAndExternalId(eq(gameId), any())).thenReturn(Optional.empty());
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        teamImportService.importTeams(gameId, externalEventId);

        // Assert
        ArgumentCaptor<Team> teamCaptor = ArgumentCaptor.forClass(Team.class);
        verify(teamRepository).save(teamCaptor.capture());

        Team savedTeam = teamCaptor.getValue();
        assertEquals("My Team Name", savedTeam.getName());
        assertEquals("My Scouting Group", savedTeam.getDescription());
    }

    @Test
    void importTeams_setsExternalIdFromBookingId() throws PreRegistrationApiException {
        // Arrange
        Registration registration = createRegistration(42, "Team Name", "Group");
        List<Registration> registrations = List.of(registration);

        when(preRegistrationClient.fetchRegistrations(externalEventId)).thenReturn(registrations);
        when(teamRepository.findByGameIdAndExternalId(eq(gameId), any())).thenReturn(Optional.empty());
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        teamImportService.importTeams(gameId, externalEventId);

        // Assert
        ArgumentCaptor<Team> teamCaptor = ArgumentCaptor.forClass(Team.class);
        verify(teamRepository).save(teamCaptor.capture());

        Team savedTeam = teamCaptor.getValue();
        assertEquals("42", savedTeam.getExternalId());
    }

    @Test
    void importTeams_initializesTeamWithDefaults() throws PreRegistrationApiException {
        // Arrange
        Registration registration = createRegistration(1, "Team Name", "Group");
        List<Registration> registrations = List.of(registration);

        when(preRegistrationClient.fetchRegistrations(externalEventId)).thenReturn(registrations);
        when(teamRepository.findByGameIdAndExternalId(eq(gameId), any())).thenReturn(Optional.empty());
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        teamImportService.importTeams(gameId, externalEventId);

        // Assert
        ArgumentCaptor<Team> teamCaptor = ArgumentCaptor.forClass(Team.class);
        verify(teamRepository).save(teamCaptor.capture());

        Team savedTeam = teamCaptor.getValue();
        assertNotNull(savedTeam.getId());
        assertEquals(gameId, savedTeam.getGameId());
        assertFalse(savedTeam.isCheckedIn());
        assertEquals(0, savedTeam.getTotalPoints());
        assertNotNull(savedTeam.getParticipants());
        assertNotNull(savedTeam.getEmergencyPhoneNumbers());
        assertNotNull(savedTeam.getRivals());
        assertNotNull(savedTeam.getCreatedAt());
        assertNotNull(savedTeam.getUpdatedAt());
    }

    @Test
    void importTeams_handlesEmptyRegistrationList() throws PreRegistrationApiException {
        // Arrange
        when(preRegistrationClient.fetchRegistrations(externalEventId)).thenReturn(new ArrayList<>());

        // Act
        ImportResult result = teamImportService.importTeams(gameId, externalEventId);

        // Assert
        assertEquals(0, result.created());
        assertEquals(0, result.updated());
        assertEquals(0, result.errors());
        verify(teamRepository, never()).save(any(Team.class));
    }

    @Test
    void importTeams_continuesOnError_andCountsErrors() throws PreRegistrationApiException {
        // Arrange
        List<Registration> registrations = List.of(
            createRegistration(1, "Team 1", "Group 1"),
            createRegistration(2, "Team 2", "Group 2"),
            createRegistration(3, "Team 3", "Group 3")
        );

        when(preRegistrationClient.fetchRegistrations(externalEventId)).thenReturn(registrations);
        when(teamRepository.findByGameIdAndExternalId(eq(gameId), any())).thenReturn(Optional.empty());

        // First and third succeed, second throws exception
        when(teamRepository.save(any(Team.class)))
            .thenAnswer(invocation -> invocation.getArgument(0))
            .thenThrow(new RuntimeException("Database error"))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        ImportResult result = teamImportService.importTeams(gameId, externalEventId);

        // Assert
        assertEquals(2, result.created());
        assertEquals(0, result.updated());
        assertEquals(1, result.errors());
    }

    @Test
    void importTeams_mixedCreateAndUpdate() throws PreRegistrationApiException {
        // Arrange
        List<Registration> registrations = List.of(
            createRegistration(1, "New Team", "New Group"),
            createRegistration(2, "Existing Team Updated", "Existing Group Updated")
        );

        Team existingTeam = new Team();
        existingTeam.setId(UUID.randomUUID());
        existingTeam.setGameId(gameId);
        existingTeam.setExternalId("2");
        existingTeam.setName("Existing Team");

        when(preRegistrationClient.fetchRegistrations(externalEventId)).thenReturn(registrations);
        when(teamRepository.findByGameIdAndExternalId(gameId, "1")).thenReturn(Optional.empty());
        when(teamRepository.findByGameIdAndExternalId(gameId, "2")).thenReturn(Optional.of(existingTeam));
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        ImportResult result = teamImportService.importTeams(gameId, externalEventId);

        // Assert
        assertEquals(1, result.created());
        assertEquals(1, result.updated());
        assertEquals(0, result.errors());
        verify(teamRepository, times(2)).save(any(Team.class));
    }

    private Registration createRegistration(int bookingId, String teamName, String scoutingGroup) {
        Registration reg = new Registration();
        reg.setBookingId(bookingId);
        reg.setEventId(externalEventId);
        reg.setTeamName(teamName);
        reg.setScoutingGroup(scoutingGroup);
        return reg;
    }
}

