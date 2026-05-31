package nl.vlettenrally.service.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.service.TeamService;
import nl.vlettenrally.websockets.WebSocketPublisher;
import nl.vlettenrally.websockets.dto.WebSocketMessageType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of TeamService
 */
@ApplicationScoped
public class TeamServiceImpl implements TeamService {

    @Inject
    TeamRepository teamRepository;

    @Inject
    WebSocketPublisher webSocketPublisher;

    @Override
    public Optional<Team> findById(UUID id) {
        return teamRepository.findById(id);
    }

    @Override
    public List<Team> findAll() {
        return teamRepository.findAll();
    }

    @Override
    public List<Team> findByGameId(UUID gameId) {
        return teamRepository.findByGameId(gameId);
    }

    @Override
    public Optional<Team> findByDeviceId(UUID deviceId) {
        return teamRepository.findByDeviceId(deviceId);
    }


    @Override
    public List<Team> findRivals(UUID teamId) {
        return teamRepository.findRivals(teamId);
    }

    @Override
    public Optional<Team> findRivalTeam(UUID teamId) {
        List<Team> rivals = teamRepository.findRivals(teamId);
        return rivals.isEmpty() ? Optional.empty() : Optional.of(rivals.getFirst());
    }

    @Override
    public Team save(Team team) {
        return teamRepository.save(team);
    }

    @Override
    public Team checkIn(UUID teamId) {
        Team result = teamRepository.findById(teamId)
            .map(team -> {
                if (team.isCheckedIn()) {
                    if (team.isCheckedInDigitally()) {
                        throw new IllegalStateException("Team is already checked in digitally");
                    }
                    team.setCheckedInDigitally(true);
                    team.setUpdatedAt(Instant.now());
                    return teamRepository.save(team);
                }
                // Validate that at least one emergency phone number is provided before check-in
                if (team.getEmergencyPhoneNumbers() == null || team.getEmergencyPhoneNumbers().isEmpty()) {
                    throw new IllegalArgumentException("At least one emergency phone number is required for check-in");
                }
                team.setCheckedIn(true);
                team.setCheckedInDigitally(true);
                team.setUpdatedAt(Instant.now());
                return teamRepository.save(team);
            })
            .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));

        // Notify any waiting devices that the details step is complete.
        if (result.getGameId() != null) {
            webSocketPublisher.sendToTeam(
                result.getGameId(),
                result.getId(),
                WebSocketMessageType.CHECKIN_DETAILS_SUBMITTED.getValue(),
                Map.of("teamId", result.getId().toString())
            );
        }

        return result;
    }

    @Override
    public void delete(UUID id) {
        teamRepository.delete(id);
    }

    @Override
    public List<Team> findRivalTeams(UUID teamId) {
        return findById(teamId)
            .map(team -> team.getRivals().stream()
                .map(this::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList())
            .orElse(List.of());
    }
}
