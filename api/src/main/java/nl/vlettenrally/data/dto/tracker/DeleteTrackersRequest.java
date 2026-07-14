package nl.vlettenrally.data.dto.tracker;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * Request DTO for bulk-deleting tracker IDs from the global pool: a flat list of
 * the IDs the admin selected.
 */
@RegisterForReflection
public record DeleteTrackersRequest(
        List<String> trackerIds
) {}
