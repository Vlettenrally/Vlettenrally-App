package nl.vlettenrally.service;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.vlettenrally.client.LocationApiClient;
import nl.vlettenrally.client.dto.LocationResponse;
import nl.vlettenrally.client.dto.ParticipantLocation;
import nl.vlettenrally.client.exception.LocationApiException;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.LocationData;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.dto.game.TeamLocationResponse;
import nl.vlettenrally.data.repository.LocationDataRepository;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.websockets.MapSubscriptionManager;
import nl.vlettenrally.websockets.WebSocketEventTypes;
import nl.vlettenrally.websockets.WebSocketMessage;
import nl.vlettenrally.websockets.WebSocketPublisher;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Scheduler that periodically polls external location APIs for active games
 * and broadcasts location updates via WebSocket.
 *
 * For each team: sends their own location via sendToTeam (team receives only their own data).
 * For admin map subscribers: sends full game locations via session-targeted messages.
 */
@ApplicationScoped
public class LocationPollingScheduler {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Inject
    GameService gameService;

    @Inject
    TeamRepository teamRepository;

    @Inject
    LocationApiClient locationApiClient;

    @Inject
    LocationDataRepository locationDataRepository;

    @Inject
    WebSocketPublisher webSocketPublisher;

    @Inject
    MapSubscriptionManager mapSubscriptionManager;

    /**
     * Poll location APIs every 15 seconds for active games with location tracking enabled.
     */
    @Scheduled(every = "15s")
    @Transactional
    void pollLocations() {
        List<Game> activeGames = gameService.findByStatus(Game.GameStatus.ACTIVE);

        for (Game game : activeGames) {
            if (!Boolean.TRUE.equals(game.getLocationEndpointActive())
                    || game.getLocationEndpointUrl() == null
                    || game.getLocationEndpointUrl().isBlank()) {
                continue;
            }

            try {
                pollGameLocations(game);
            } catch (Exception e) {
                Log.errorf("Failed to poll locations for game %s: %s", game.getId(), e.getMessage());
            }
        }
    }

    private void pollGameLocations(Game game) {
        LocationResponse response;
        try {
            response = locationApiClient.fetchLocations(game.getLocationEndpointUrl());
        } catch (LocationApiException e) {
            Log.warnf("Location API error for game %s: %s", game.getId(), e.getMessage());
            return;
        }

        if (response.getSocialrunParticipantsLocations() == null
                || response.getSocialrunParticipantsLocations().getParticipantLocations() == null) {
            return;
        }

        List<ParticipantLocation> participantLocations = response.getSocialrunParticipantsLocations().getParticipantLocations();

        // Map teams by externalId for matching
        List<Team> teams = teamRepository.findByGameId(game.getId());
        Map<String, Team> teamsByExternalId = teams.stream()
                .filter(t -> t.getExternalId() != null)
                .collect(Collectors.toMap(Team::getExternalId, t -> t, (a, b) -> a));

        // Also map by bib label for fallback matching
        Map<String, Team> teamsByName = teams.stream()
                .collect(Collectors.toMap(t -> t.getName().toLowerCase().trim(), t -> t, (a, b) -> a));

        List<TeamLocationResponse> allLocations = new ArrayList<>();

        for (ParticipantLocation pl : participantLocations) {
            if (pl.getLocation() == null) continue;

            // Match participant to team
            Team team = teamsByExternalId.get(String.valueOf(pl.getEventParticipantsId()));
            if (team == null && pl.getBibLabel() != null) {
                team = teamsByName.get(pl.getBibLabel().toLowerCase().trim());
            }
            if (team == null) continue;

            // Create PostGIS Point (longitude = x, latitude = y)
            Point point = GEOMETRY_FACTORY.createPoint(
                    new Coordinate(pl.getLocation().getLongitude(), pl.getLocation().getLatitude()));

            // Parse location time
            Instant locationTime;
            try {
                locationTime = pl.getLocationTime() != null ? Instant.parse(pl.getLocationTime()) : Instant.now();
            } catch (Exception e) {
                locationTime = Instant.now();
            }

            // Save location data
            LocationData locationData = new LocationData();
            locationData.setTeamId(team.getId());
            locationData.setGameId(game.getId());
            locationData.setLocation(point);
            locationData.setLocationTime(locationTime);
            locationData.setAccuracy(pl.getGpsAccuracy() > 0 ? BigDecimal.valueOf(pl.getGpsAccuracy()) : null);
            locationData.setSpeed(pl.getLocation().getLatitude() != 0 ? null : null); // Speed not in external API
            locationData.setHeading(null);

            locationDataRepository.save(locationData);

            // Build DTO for WebSocket broadcast
            TeamLocationResponse teamLocation = new TeamLocationResponse(
                    team.getId(),
                    team.getName(),
                    pl.getLocation().getLatitude(),
                    pl.getLocation().getLongitude(),
                    null, // speed
                    null, // heading
                    locationTime.toString()
            );
            allLocations.add(teamLocation);

            // Send to the team itself (they only receive their own location)
            webSocketPublisher.sendToTeam(
                    game.getId(),
                    team.getId(),
                    WebSocketEventTypes.LOCATION_UPDATE,
                    teamLocation
            );
        }

        // Send all locations to admin map subscribers via session-targeted messages
        if (!allLocations.isEmpty()) {
            Set<String> subscribedSessionIds = mapSubscriptionManager.getActiveSubscribers(game.getId());
            for (String sessionId : subscribedSessionIds) {
                WebSocketMessage message = new WebSocketMessage(WebSocketEventTypes.LOCATION_UPDATE, allLocations)
                        .withTargetGame(game.getId())
                        .forAdminOnly()
                        .withTargetSession(sessionId);
                webSocketPublisher.publish(message);
            }
        }
    }
}

