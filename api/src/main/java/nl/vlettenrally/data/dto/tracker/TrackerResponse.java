package nl.vlettenrally.data.dto.tracker;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;
import java.util.List;

/**
 * One tracker in the global pool, with its last reported health status and
 * every team/game currently holding it (empty when unassigned).
 */
@RegisterForReflection
public record TrackerResponse(
        String trackerId,
        boolean enabled,
        Integer batteryPercentage,
        Instant lastSeenAt,
        Double latitude,
        Double longitude,
        List<TrackerAssignmentResponse> assignments
) {}
