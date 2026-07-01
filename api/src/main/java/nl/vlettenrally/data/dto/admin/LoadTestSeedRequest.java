package nl.vlettenrally.data.dto.admin;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Request body for {@code POST /api/v1/admin/load-test/seed}. All fields are optional;
 * omitted values fall back to sensible load-test defaults (see AdminLoadTestResource).
 *
 * @param teamCount           number of teams to create (each gets a numeric trackerId)
 * @param checkpointCount     number of checkpoints (also the movement waypoints)
 * @param centerLat           centre latitude the checkpoints are spread around
 * @param centerLng           centre longitude the checkpoints are spread around
 * @param spreadKm            radius (km) the checkpoints are placed on around the centre
 * @param locationEndpointUrl base URL the API will poll for participant locations; should
 *                            point at the mock location server (e.g. the in-cluster service)
 * @param seedAutomations     whether to also seed automation rules that exercise the engine
 *                            (distance accrual, checkpoint award/message, speed alert, rival
 *                            overtake). Null = true.
 * @param startInMinutes      minutes from now until the game auto-starts (PLANNED → ACTIVE). The
 *                            game is created not-yet-started so teams check in and move during a
 *                            pre-start window. Null = default (7).
 */
@RegisterForReflection
public record LoadTestSeedRequest(
    Integer teamCount,
    Integer checkpointCount,
    Double centerLat,
    Double centerLng,
    Double spreadKm,
    String locationEndpointUrl,
    Boolean seedAutomations,
    Integer startInMinutes
) {}
