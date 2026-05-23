package nl.vlettenrally.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.vlettenrally.client.PreRegistrationApiClient;
import nl.vlettenrally.client.dto.Attendee;
import nl.vlettenrally.client.dto.Registration;
import nl.vlettenrally.client.exception.PreRegistrationApiException;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.repository.TeamRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for importing teams from external pre-registration system.
 *
 * Each booking from the external API contains one or more boat attendees;
 * each attendee is imported as its own Team (one team per boat).
 */
@ApplicationScoped
public class TeamImportService {

    @Inject
    PreRegistrationApiClient preRegistrationClient;

    @Inject
    TeamRepository teamRepository;

    /**
     * Import teams from the pre-registration API for a specific game.
     *
     * @param gameId The game to import teams into
     * @param externalEventId The external event ID to fetch registrations from
     * @return Import result with counts of created, updated, and failed teams
     */
    @Transactional
    public ImportResult importTeams(UUID gameId, int externalEventId)
            throws PreRegistrationApiException {

        List<Registration> bookings = preRegistrationClient.fetchRegistrations(externalEventId);

        int created = 0;
        int updated = 0;
        int errors = 0;

        for (Registration booking : bookings) {
            List<Attendee> attendees = booking.getAttendees();
            if (attendees == null || attendees.isEmpty()) {
                continue;
            }

            for (Attendee attendee : attendees) {
                try {
                    String externalId = String.valueOf(attendee.getTicketBookingId());

                    Optional<Team> existingTeam = teamRepository.findByGameIdAndExternalId(gameId, externalId);

                    if (existingTeam.isPresent()) {
                        Team team = existingTeam.get();
                        updateTeamFromAttendee(team, booking, attendee);
                        teamRepository.save(team);
                        updated++;
                    } else {
                        Team team = createTeamFromAttendee(gameId, booking, attendee);
                        teamRepository.save(team);
                        created++;
                    }
                } catch (Exception e) {
                    errors++;
                    Log.error("Failed to import team for booking " + booking.getBookingId()
                            + " boat " + attendee.getBoatNumber() + ": " + e.getMessage());
                }
            }
        }

        return new ImportResult(created, updated, errors);
    }

    private Team createTeamFromAttendee(UUID gameId, Registration booking, Attendee attendee) {
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setGameId(gameId);
        team.setExternalId(String.valueOf(attendee.getTicketBookingId()));
        team.setName(attendee.getBoatNumber());
        team.setDescription(booking.getScoutingGroup());
        team.setNotes(attendee.getNotes());
        team.setParticipants(new ArrayList<>());
        team.setEmergencyPhoneNumbers(new ArrayList<>());
        team.setCheckedIn(false);
        team.setTotalPoints(0);
        team.setRivals(new ArrayList<>());
        team.setCreatedAt(Instant.now());
        team.setUpdatedAt(Instant.now());
        return team;
    }

    private void updateTeamFromAttendee(Team team, Registration booking, Attendee attendee) {
        team.setName(attendee.getBoatNumber());
        team.setDescription(booking.getScoutingGroup());
        team.setNotes(attendee.getNotes());
        team.setUpdatedAt(Instant.now());
    }
}
