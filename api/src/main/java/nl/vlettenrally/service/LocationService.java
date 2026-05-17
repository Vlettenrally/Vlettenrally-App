package nl.vlettenrally.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import nl.vlettenrally.data.dto.game.TeamLocationResponse;

/**
 * Service interface for read-only location queries used by the admin map.
 *
 * Both methods return {@code Optional.empty()} when the underlying entity
 * (game or team-within-game) cannot be found, so callers can translate that
 * to a 404 without performing their own existence checks.
 */
public interface LocationService {

    /**
     * Latest known location per team in the game.
     * @return empty when the game does not exist
     */
    Optional<List<TeamLocationResponse>> getLatestTeamLocations(UUID gameId);

    /**
     * Recent location history for a single team, sorted ascending by locationTime
     * so the result can be fed directly into a polyline.
     *
     * @param windowMinutes how far back to look; clamped to a service-defined ceiling
     * @return empty when the game does not exist or the team does not belong to it
     */
    Optional<List<TeamLocationResponse>> getTeamLocationHistory(UUID gameId, UUID teamId, int windowMinutes);
}
