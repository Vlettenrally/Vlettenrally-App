package nl.vlettenrally.service.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.service.TeamService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of TeamService
 */
@ApplicationScoped
public class TeamServiceImpl implements TeamService {

    @Inject
    TeamRepository teamRepository;

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
    public Optional<Team> findByBoatNumber(String boatNumber) {
        return teamRepository.findByBoatNumber(boatNumber);
    }

    @Override
    public List<Team> findRivals(UUID teamId) {
        return teamRepository.findRivals(teamId);
    }

    @Override
    public Optional<Team> findRivalTeam(UUID teamId) {
        List<Team> rivals = teamRepository.findRivals(teamId);
        return rivals.isEmpty() ? Optional.empty() : Optional.of(rivals.get(0));
    }

    @Override
    public Team save(Team team) {
        return teamRepository.save(team);
    }

    @Override
    public Team checkIn(UUID teamId) {
        return teamRepository.findById(teamId)
            .map(team -> {
                if (team.isCheckedIn()) {
                    throw new IllegalStateException("Team is already checked in");
                }
                // Validate that at least one emergency phone number is provided before check-in
                if (team.getEmergencyPhoneNumbers() == null || team.getEmergencyPhoneNumbers().isEmpty()) {
                    throw new IllegalArgumentException("At least one emergency phone number is required for check-in");
                }
                team.setCheckedIn(true);
                team.setUpdatedAt(Instant.now());
                return teamRepository.save(team);
            })
            .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));
    }

    @Override
    public Team updatePoints(UUID teamId, int points) {
        return teamRepository.findById(teamId)
            .map(team -> {
                team.setTotalPoints(points);
                team.setUpdatedAt(Instant.now());
                return teamRepository.save(team);
            })
            .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));
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
