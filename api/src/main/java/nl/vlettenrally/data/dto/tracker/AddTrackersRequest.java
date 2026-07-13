package nl.vlettenrally.data.dto.tracker;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * Request DTO for bulk-adding tracker IDs to the global pool: a flat list of
 * IDs pasted by the admin.
 */
@RegisterForReflection
public record AddTrackersRequest(
        List<String> trackerIds
) {}
