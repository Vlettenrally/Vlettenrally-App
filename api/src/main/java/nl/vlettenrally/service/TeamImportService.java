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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Service for importing teams from external pre-registration system.
 * <p>
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
    public TeamImportResult importTeams(UUID gameId, int externalEventId)
            throws PreRegistrationApiException {

        List<Registration> bookings = preRegistrationClient.fetchRegistrations(externalEventId);

        List<String> createdTeams = new ArrayList<>();
        List<String> updatedTeams = new ArrayList<>();
        List<String> skippedDuplicates = new ArrayList<>();
        List<String> errorDetails = new ArrayList<>();

        // Boat numbers already present for this game (from earlier imports or this run).
        // The external API sometimes returns the same boat under multiple ticket booking
        // ids; we import each boat at most once. Seeding from existing teams keeps the
        // dedup robust across re-imports and independent of attendee ordering.
        Set<String> seenBoatNumbers = new HashSet<>();
        for (Team existing : teamRepository.findByGameId(gameId)) {
            String key = normalizeBoatNumber(existing.getName());
            if (key != null) {
                seenBoatNumbers.add(key);
            }
        }

        for (Registration booking : bookings) {
            List<Attendee> attendees = booking.getAttendees();
            if (attendees == null || attendees.isEmpty()) {
                continue;
            }

            for (Attendee attendee : attendees) {
                String boatNumber = attendee.getBoatNumber();
                String boatKey = normalizeBoatNumber(boatNumber);
                try {
                    String externalId = String.valueOf(attendee.getTicketBookingId());

                    Optional<Team> existingTeam = teamRepository.findByGameIdAndExternalId(gameId, externalId);

                    if (existingTeam.isPresent()) {
                        Team team = existingTeam.get();
                        updateTeamFromAttendee(team, booking, attendee);
                        teamRepository.save(team);
                        updatedTeams.add(boatNumber);
                        if (boatKey != null) {
                            seenBoatNumbers.add(boatKey);
                        }
                    } else if (boatKey != null && seenBoatNumbers.contains(boatKey)) {
                        // Same boat already imported for this game: skip the duplicate.
                        skippedDuplicates.add(boatNumber);
                    } else {
                        Team team = createTeamFromAttendee(gameId, booking, attendee);
                        teamRepository.save(team);
                        createdTeams.add(boatNumber);
                        if (boatKey != null) {
                            seenBoatNumbers.add(boatKey);
                        }
                    }
                } catch (Exception e) {
                    String detail = "booking " + booking.getBookingId()
                            + " boat " + boatNumber + ": " + e.getMessage();
                    errorDetails.add(detail);
                    Log.error("Failed to import team for " + detail);
                }
            }
        }

        return new TeamImportResult(
                createdTeams.size(),
                updatedTeams.size(),
                skippedDuplicates.size(),
                errorDetails.size(),
                createdTeams,
                updatedTeams,
                skippedDuplicates,
                errorDetails
        );
    }

    /**
     * Normalize a boat number for duplicate comparison. Blank names are not deduped
     * (returns {@code null}), so attendees without a boat number are still imported.
     */
    private String normalizeBoatNumber(String boatNumber) {
        if (boatNumber == null) {
            return null;
        }
        String trimmed = boatNumber.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
