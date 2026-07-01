package nl.vlettenrally.data.dto.admin;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The generated load-test scenario. Returned by the seed endpoint and consumed by both the
 * mock location server (waypoints + trackerIds) and the k6 driver (gameId + team ids).
 */
@RegisterForReflection
public record LoadTestScenarioResponse(
    UUID gameId,
    String gameName,
    String locationEndpointUrl,
    Instant startTime,
    List<Waypoint> checkpoints,
    List<TeamRef> teams
) {
    /** A checkpoint the API geofences on and the mock server steers teams towards. */
    @RegisterForReflection
    public record Waypoint(String name, double lat, double lng) {}

    /** A seeded team: {@code id} for app auth, {@code trackerId} for location matching. */
    @RegisterForReflection
    public record TeamRef(UUID id, String name, String trackerId) {}
}
