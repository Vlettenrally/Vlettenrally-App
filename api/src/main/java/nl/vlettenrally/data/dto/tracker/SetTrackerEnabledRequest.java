package nl.vlettenrally.data.dto.tracker;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Request DTO for enabling/disabling a tracker in the global pool.
 */
@RegisterForReflection
public record SetTrackerEnabledRequest(
        boolean enabled
) {}
