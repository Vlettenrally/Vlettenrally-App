package nl.vlettenrally.service.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.LocationData;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.dto.game.TeamLocationResponse;
import nl.vlettenrally.data.repository.LocationDataRepository;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.service.GameService;
import nl.vlettenrally.service.LocationService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class LocationServiceImpl implements LocationService {

    static final int MAX_HISTORY_WINDOW_MINUTES = 240;

    @Inject
    LocationDataRepository locationDataRepository;

    @Inject
    TeamRepository teamRepository;

    @Inject
    GameService gameService;

    @Override
    public Optional<List<TeamLocationResponse>> getLatestTeamLocations(UUID gameId) {
        if (gameService.findById(gameId).isEmpty()) {
            return Optional.empty();
        }

        List<Team> teams = teamRepository.findByGameId(gameId);
        Map<UUID, Team> teamMap = teams.stream()
            .collect(Collectors.toMap(Team::getId, t -> t));

        List<TeamLocationResponse> locations = new ArrayList<>();
        for (Team team : teams) {
            locationDataRepository.findLatestByTeamId(team.getId())
                .ifPresent(loc -> locations.add(toResponse(loc, teamMap.get(loc.getTeamId()))));
        }
        return Optional.of(locations);
    }

    @Override
    public Optional<List<TeamLocationResponse>> getTeamLocationHistory(UUID gameId, UUID teamId, int windowMinutes) {
        if (gameService.findById(gameId).isEmpty()) {
            return Optional.empty();
        }

        Team team = teamRepository.findByGameId(gameId).stream()
            .filter(t -> teamId.equals(t.getId()))
            .findFirst()
            .orElse(null);
        if (team == null) {
            return Optional.empty();
        }

        int clamped = Math.max(1, Math.min(windowMinutes, MAX_HISTORY_WINDOW_MINUTES));
        Instant end = Instant.now();
        Instant start = end.minus(clamped, ChronoUnit.MINUTES);

        List<TeamLocationResponse> points = locationDataRepository.findByTeamIdAndTimeRange(teamId, start, end).stream()
            .sorted(Comparator.comparing(LocationData::getLocationTime))
            .map(loc -> toResponse(loc, team))
            .collect(Collectors.toList());

        return Optional.of(points);
    }

    private static TeamLocationResponse toResponse(LocationData loc, Team team) {
        double lat = loc.getLocation().getY();
        double lng = loc.getLocation().getX();
        Double speed = loc.getSpeed() != null ? loc.getSpeed().doubleValue() : null;
        Double heading = loc.getHeading() != null ? loc.getHeading().doubleValue() : null;
        String locationTime = loc.getLocationTime() != null ? loc.getLocationTime().toString() : null;
        return new TeamLocationResponse(
            team.getId(),
            team.getName(),
            lat,
            lng,
            speed,
            heading,
            locationTime
        );
    }
}
