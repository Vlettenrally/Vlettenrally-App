package nl.vlettenrally.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.vlettenrally.client.PreRegistrationApiClient;
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
 * Service for importing teams from external pre-registration system
 */
@ApplicationScoped
public class TeamImportService {

    @Inject
    PreRegistrationApiClient preRegistrationClient;

    @Inject
    TeamRepository teamRepository;

    /**
     * Import teams from the pre-registration API for a specific game
     * 
     * @param gameId The game to import teams into
     * @param externalEventId The external event ID to fetch registrations from
     * @return Import result with counts of created, updated, and failed teams
     */
    @Transactional
    public ImportResult importTeams(UUID gameId, int externalEventId) 
            throws PreRegistrationApiException {
        
        List<Registration> registrations = preRegistrationClient.fetchRegistrations(externalEventId);
        
        int created = 0;
        int updated = 0;
        int errors = 0;
        
        for (Registration registration : registrations) {
            try {
                String externalId = String.valueOf(registration.getBookingId());
                
                // Check if team already exists
                Optional<Team> existingTeam = teamRepository.findByGameIdAndExternalId(gameId, externalId);
                
                if (existingTeam.isPresent()) {
                    // Update existing team
                    Team team = existingTeam.get();
                    updateTeamFromRegistration(team, registration);
                    teamRepository.save(team);
                    updated++;
                } else {
                    // Create new team
                    Team team = createTeamFromRegistration(gameId, registration);
                    teamRepository.save(team);
                    created++;
                }
            } catch (Exception e) {
                errors++;
                // Log error but continue with other teams
                Log.error("Failed to import team " + registration.getBoatNumber() + ": " + e.getMessage());
            }
        }
        
        return new ImportResult(created, updated, errors);
    }
    
    private Team createTeamFromRegistration(UUID gameId, Registration registration) {
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setGameId(gameId);
        team.setExternalId(String.valueOf(registration.getBookingId()));
        team.setName(registration.getBoatNumber()); // boat_number is actually the team name
        team.setDescription(registration.getScoutingGroup());
        team.setBoatNumber(null); // Will be assigned later
        team.setParticipants(new ArrayList<>());
        team.setEmergencyPhoneNumbers(new ArrayList<>());
        team.setCheckedIn(false);
        team.setTotalPoints(0);
        team.setRivals(new ArrayList<>());
        team.setCreatedAt(Instant.now());
        team.setUpdatedAt(Instant.now());
        return team;
    }
    
    private void updateTeamFromRegistration(Team team, Registration registration) {
        // Only update certain fields to preserve local changes
        team.setName(registration.getBoatNumber());
        team.setDescription(registration.getScoutingGroup());
        team.setUpdatedAt(Instant.now());
    }
}
