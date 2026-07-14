package nl.vlettenrally.service.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.enums.CheckInMethod;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.service.AutomationEngine;
import nl.vlettenrally.service.GameService;
import nl.vlettenrally.service.PictureService;
import nl.vlettenrally.service.PointsTotalsCache;
import nl.vlettenrally.service.TeamService;
import nl.vlettenrally.websockets.WebSocketPublisher;
import nl.vlettenrally.websockets.dto.WebSocketMessageType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Implementation of TeamService
 */
@ApplicationScoped
public class TeamServiceImpl implements TeamService {

    @Inject
    TeamRepository teamRepository;

    @Inject
    GameService gameService;

    @Inject
    WebSocketPublisher webSocketPublisher;

    @Inject
    PictureService pictureService;

    @Inject
    AutomationEngine automationEngine;

    @Inject
    PointsTotalsCache pointsTotalsCache;

    @ConfigProperty(name = "vlettenrally.rivals.max-per-team", defaultValue = "5")
    int maxRivals;

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
    public Optional<Team> findByTraccarIdentifier(String traccarIdentifier) {
        return teamRepository.findByTraccarIdentifier(traccarIdentifier);
    }


    @Override
    public List<Team> findRivals(UUID teamId) {
        return teamRepository.findRivals(teamId);
    }

    @Override
    public Team save(Team team) {
        // Generate the long-lived Traccar secret the first time tracking is enabled.
        // Kept across disable/re-enable so an already-configured app stays valid.
        if (team.isTraccarEnabled() && team.getTraccarIdentifier() == null) {
            team.setTraccarIdentifier(generateTraccarIdentifier());
        }
        return teamRepository.save(team);
    }

    /** 32 random bytes, URL-safe base64 (no padding) — safe to drop into a query string. */
    private static String generateTraccarIdentifier() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
                    team.setCheckInMethod(CheckInMethod.DIGITAL);
                    team.setUpdatedAt(Instant.now());
                    return teamRepository.save(team);
                }
                // Validate that at least one emergency phone number is provided before check-in
                if (team.getEmergencyPhoneNumbers() == null || team.getEmergencyPhoneNumbers().isEmpty()) {
                    throw new IllegalArgumentException("At least one emergency phone number is required for check-in");
                }
                Instant now = Instant.now();
                team.setCheckedIn(true);
                team.setCheckedInDigitally(true);
                team.setCheckInMethod(CheckInMethod.DIGITAL);
                team.setCheckedInAt(now);
                team.setUpdatedAt(now);
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
            automationEngine.onTeamCheckIn(result.getGameId(), result.getId());
        }

        return result;
    }

    @Override
    public void delete(UUID id) {
        // Stored picture objects don't cascade with the team's DB row — remove them first.
        pictureService.deleteAllForTeam(id);
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

    @Override
    public List<Team> setRivals(UUID teamId, List<UUID> rivalIds) {
        if (rivalIds == null) {
            throw new IllegalArgumentException("rivalTeamIds is required");
        }

        Team team = teamRepository.findById(teamId)
            .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));

        if (team.getGameId() != null) {
            Game game = gameService.findById(team.getGameId())
                .orElseThrow(() -> new IllegalArgumentException("Game not found for team"));
            if (game.getStatus() == Game.GameStatus.COMPLETED) {
                throw new IllegalStateException("Rival selection is closed for this game");
            }
        }

        if (rivalIds.size() > maxRivals) {
            throw new IllegalArgumentException("A team can have at most " + maxRivals + " rivals");
        }

        Set<UUID> deduped = new HashSet<>(rivalIds);
        if (deduped.size() != rivalIds.size()) {
            throw new IllegalArgumentException("Duplicate rival ids");
        }
        if (deduped.contains(teamId)) {
            throw new IllegalArgumentException("A team cannot be its own rival");
        }

        // Every rival must belong to the same game.
        List<Team> validated = new ArrayList<>(rivalIds.size());
        for (UUID rivalId : rivalIds) {
            Team rival = teamRepository.findById(rivalId)
                .orElseThrow(() -> new IllegalArgumentException("Rival team not found: " + rivalId));
            if (!Objects.equals(rival.getGameId(), team.getGameId())) {
                throw new IllegalArgumentException("Rival must belong to the same game: " + rivalId);
            }
            validated.add(rival);
        }

        team.setRivals(new ArrayList<>(rivalIds));
        team.setUpdatedAt(Instant.now());
        teamRepository.save(team);

        if (team.getGameId() != null) {
            webSocketPublisher.sendToTeamOnly(
                team.getGameId(),
                team.getId(),
                WebSocketMessageType.RIVAL_UPDATE.getValue(),
                validated.stream()
                    .map(r -> Map.of(
                        "id", r.getId(),
                        "name", r.getName(),
                        "totalPoints", pointsTotalsCache.get(r.getId())
                    ))
                    .toList()
            );
        }

        return validated;
    }
}
