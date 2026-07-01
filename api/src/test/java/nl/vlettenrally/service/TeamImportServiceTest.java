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
import nl.vlettenrally.client.dto.Attendee;
import nl.vlettenrally.client.dto.Registration;
import nl.vlettenrally.client.exception.PreRegistrationApiException;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.repository.TeamRepository;

/**
 * Unit tests for TeamImportService.
 * Each attendee inside a booking becomes its own Team.
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
    void importTeams_createsNewTeams_oneTeamPerAttendee() throws PreRegistrationApiException {
        List<Registration> bookings = List.of(
            createBooking(299, "Bas 't Hart",
                attendee(897, "1001", "4", ""),
                attendee(898, "1623", "4", "")),
            createBooking(298, "Cornelis de Houtman",
                attendee(893, "642", "5", ""))
        );

        when(preRegistrationClient.fetchRegistrations(externalEventId)).thenReturn(bookings);
        when(teamRepository.findByGameIdAndExternalId(eq(gameId), any())).thenReturn(Optional.empty());
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TeamImportResult result = teamImportService.importTeams(gameId, externalEventId);

        assertEquals(3, result.created());
        assertEquals(0, result.updated());
        assertEquals(0, result.errors());
        verify(teamRepository, times(3)).save(any(Team.class));
    }

    @Test
    void importTeams_updatesExistingTeams_keyedByTicketBookingId() throws PreRegistrationApiException {
        Registration booking = createBooking(299, "Updated Group",
            attendee(897, "1001-new", "5", "updated notes"));

        Team existingTeam = new Team();
        existingTeam.setId(UUID.randomUUID());
        existingTeam.setGameId(gameId);
        existingTeam.setExternalId("897");
        existingTeam.setName("1001-old");
        existingTeam.setDescription("Old Group");

        when(preRegistrationClient.fetchRegistrations(externalEventId)).thenReturn(List.of(booking));
        when(teamRepository.findByGameIdAndExternalId(gameId, "897")).thenReturn(Optional.of(existingTeam));
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TeamImportResult result = teamImportService.importTeams(gameId, externalEventId);

        assertEquals(0, result.created());
        assertEquals(1, result.updated());
        assertEquals(0, result.errors());

        ArgumentCaptor<Team> teamCaptor = ArgumentCaptor.forClass(Team.class);
        verify(teamRepository).save(teamCaptor.capture());

        Team savedTeam = teamCaptor.getValue();
        assertEquals("1001-new", savedTeam.getName());
        assertEquals("Updated Group", savedTeam.getDescription());
        assertEquals("updated notes", savedTeam.getNotes());
    }

    @Test
    void importTeams_mapsTeamNameFromBoatNumber() throws PreRegistrationApiException {
        Registration booking = createBooking(299, "Scouting Den Hoorn",
            attendee(891, "408", "5", "Hij is rood"));

        when(preRegistrationClient.fetchRegistrations(externalEventId)).thenReturn(List.of(booking));
        when(teamRepository.findByGameIdAndExternalId(eq(gameId), any())).thenReturn(Optional.empty());
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> invocation.getArgument(0));

        teamImportService.importTeams(gameId, externalEventId);

        ArgumentCaptor<Team> teamCaptor = ArgumentCaptor.forClass(Team.class);
        verify(teamRepository).save(teamCaptor.capture());

        Team savedTeam = teamCaptor.getValue();
        assertEquals("408", savedTeam.getName());
        assertEquals("Scouting Den Hoorn", savedTeam.getDescription());
        assertEquals("Hij is rood", savedTeam.getNotes());
    }

    @Test
    void importTeams_setsExternalIdFromTicketBookingId() throws PreRegistrationApiException {
        Registration booking = createBooking(299, "Group",
            attendee(897, "1001", "4", ""));

        when(preRegistrationClient.fetchRegistrations(externalEventId)).thenReturn(List.of(booking));
        when(teamRepository.findByGameIdAndExternalId(eq(gameId), any())).thenReturn(Optional.empty());
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> invocation.getArgument(0));

        teamImportService.importTeams(gameId, externalEventId);

        ArgumentCaptor<Team> teamCaptor = ArgumentCaptor.forClass(Team.class);
        verify(teamRepository).save(teamCaptor.capture());

        Team savedTeam = teamCaptor.getValue();
        assertEquals("897", savedTeam.getExternalId());
    }

    @Test
    void importTeams_initializesTeamWithDefaults() throws PreRegistrationApiException {
        Registration booking = createBooking(299, "Group",
            attendee(897, "1001", "4", ""));

        when(preRegistrationClient.fetchRegistrations(externalEventId)).thenReturn(List.of(booking));
        when(teamRepository.findByGameIdAndExternalId(eq(gameId), any())).thenReturn(Optional.empty());
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> invocation.getArgument(0));

        teamImportService.importTeams(gameId, externalEventId);

        ArgumentCaptor<Team> teamCaptor = ArgumentCaptor.forClass(Team.class);
        verify(teamRepository).save(teamCaptor.capture());

        Team savedTeam = teamCaptor.getValue();
        assertNotNull(savedTeam.getId());
        assertEquals(gameId, savedTeam.getGameId());
        assertFalse(savedTeam.isCheckedIn());
        assertNotNull(savedTeam.getParticipants());
        assertNotNull(savedTeam.getEmergencyPhoneNumbers());
        assertTrue(savedTeam.getEmergencyPhoneNumbers().isEmpty());
        assertNotNull(savedTeam.getRivals());
        assertNotNull(savedTeam.getCreatedAt());
        assertNotNull(savedTeam.getUpdatedAt());
    }

    @Test
    void importTeams_handlesEmptyBookingList() throws PreRegistrationApiException {
        when(preRegistrationClient.fetchRegistrations(externalEventId)).thenReturn(new ArrayList<>());

        TeamImportResult result = teamImportService.importTeams(gameId, externalEventId);

        assertEquals(0, result.created());
        assertEquals(0, result.updated());
        assertEquals(0, result.errors());
        verify(teamRepository, never()).save(any(Team.class));
    }

    @Test
    void importTeams_skipsBookingsWithNoAttendees() throws PreRegistrationApiException {
        Registration emptyBooking = createBooking(299, "Empty Group");
        Registration nullAttendeesBooking = new Registration();
        nullAttendeesBooking.setBookingId(300);
        nullAttendeesBooking.setScoutingGroup("Null Attendees");
        nullAttendeesBooking.setAttendees(null);

        when(preRegistrationClient.fetchRegistrations(externalEventId))
            .thenReturn(List.of(emptyBooking, nullAttendeesBooking));

        TeamImportResult result = teamImportService.importTeams(gameId, externalEventId);

        assertEquals(0, result.created());
        assertEquals(0, result.updated());
        assertEquals(0, result.errors());
        verify(teamRepository, never()).save(any(Team.class));
    }

    @Test
    void importTeams_continuesOnError_andCountsErrors() throws PreRegistrationApiException {
        Registration booking = createBooking(299, "Group",
            attendee(1, "B1", "4", ""),
            attendee(2, "B2", "4", ""),
            attendee(3, "B3", "4", ""));

        when(preRegistrationClient.fetchRegistrations(externalEventId)).thenReturn(List.of(booking));
        when(teamRepository.findByGameIdAndExternalId(eq(gameId), any())).thenReturn(Optional.empty());

        when(teamRepository.save(any(Team.class)))
            .thenAnswer(invocation -> invocation.getArgument(0))
            .thenThrow(new RuntimeException("Database error"))
            .thenAnswer(invocation -> invocation.getArgument(0));

        TeamImportResult result = teamImportService.importTeams(gameId, externalEventId);

        assertEquals(2, result.created());
        assertEquals(0, result.updated());
        assertEquals(1, result.errors());
    }

    @Test
    void importTeams_mixedCreateAndUpdate() throws PreRegistrationApiException {
        Registration booking = createBooking(299, "Group",
            attendee(1, "B1", "4", ""),
            attendee(2, "B2-updated", "4", ""));

        Team existingTeam = new Team();
        existingTeam.setId(UUID.randomUUID());
        existingTeam.setGameId(gameId);
        existingTeam.setExternalId("2");
        existingTeam.setName("B2-old");

        when(preRegistrationClient.fetchRegistrations(externalEventId)).thenReturn(List.of(booking));
        when(teamRepository.findByGameIdAndExternalId(gameId, "1")).thenReturn(Optional.empty());
        when(teamRepository.findByGameIdAndExternalId(gameId, "2")).thenReturn(Optional.of(existingTeam));
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TeamImportResult result = teamImportService.importTeams(gameId, externalEventId);

        assertEquals(1, result.created());
        assertEquals(1, result.updated());
        assertEquals(0, result.errors());
        verify(teamRepository, times(2)).save(any(Team.class));
    }

    @Test
    void importTeams_skipsDuplicateBoatNumbers_withinSameRun() throws PreRegistrationApiException {
        // External API returns the same boat twice under different ticket booking ids.
        Registration booking = createBooking(299, "Scouting Den Hoorn",
            attendee(897, "642", "4", ""),
            attendee(898, "642", "5", "duplicate"));

        when(preRegistrationClient.fetchRegistrations(externalEventId)).thenReturn(List.of(booking));
        when(teamRepository.findByGameIdAndExternalId(eq(gameId), any())).thenReturn(Optional.empty());
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TeamImportResult result = teamImportService.importTeams(gameId, externalEventId);

        assertEquals(1, result.created());
        assertEquals(0, result.updated());
        assertEquals(1, result.skipped());
        assertEquals(0, result.errors());
        assertEquals(List.of("642"), result.createdTeams());
        assertEquals(List.of("642"), result.skippedDuplicates());
        verify(teamRepository, times(1)).save(any(Team.class));
    }

    @Test
    void importTeams_skipsBoatAlreadyPresentFromEarlierImport_regardlessOfTicketId()
            throws PreRegistrationApiException {
        // Boat "642" already exists for this game (imported earlier under ticket 893).
        // The API now returns it again under a different ticket id 999; it must not be
        // duplicated, even though the ticket id no longer matches.
        Team existingTeam = new Team();
        existingTeam.setId(UUID.randomUUID());
        existingTeam.setGameId(gameId);
        existingTeam.setExternalId("893");
        existingTeam.setName("642");

        Registration booking = createBooking(298, "Cornelis de Houtman",
            attendee(999, "642", "5", ""));

        when(preRegistrationClient.fetchRegistrations(externalEventId)).thenReturn(List.of(booking));
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(existingTeam));
        when(teamRepository.findByGameIdAndExternalId(eq(gameId), any())).thenReturn(Optional.empty());

        TeamImportResult result = teamImportService.importTeams(gameId, externalEventId);

        assertEquals(0, result.created());
        assertEquals(0, result.updated());
        assertEquals(1, result.skipped());
        assertEquals(List.of("642"), result.skippedDuplicates());
        verify(teamRepository, never()).save(any(Team.class));
    }

    private Registration createBooking(int bookingId, String scoutingGroup, Attendee... attendees) {
        Registration booking = new Registration();
        booking.setBookingId(bookingId);
        booking.setEventId(externalEventId);
        booking.setScoutingGroup(scoutingGroup);
        booking.setAttendees(new ArrayList<>(List.of(attendees)));
        booking.setBoatCount(attendees.length);
        return booking;
    }

    private Attendee attendee(int ticketBookingId, String boatNumber, String participantCount, String notes) {
        Attendee attendee = new Attendee();
        attendee.setTicketBookingId(ticketBookingId);
        attendee.setBoatNumber(boatNumber);
        attendee.setParticipantCount(participantCount);
        attendee.setNotes(notes);
        return attendee;
    }
}
