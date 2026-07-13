package nl.vlettenrally.data.dto.tracker;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Result of bulk-adding tracker IDs: how many were new vs. already in the pool.
 */
@RegisterForReflection
public record AddTrackersResponse(
        int added,
        int duplicates
) {}
