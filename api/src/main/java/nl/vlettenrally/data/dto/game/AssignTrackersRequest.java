package nl.vlettenrally.data.dto.game;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * Request DTO for bulk tracker assignment: a flat list of tracker IDs pasted by
 * the admin, auto-distributed across teams that don't have a tracker yet.
 */
@RegisterForReflection
public record AssignTrackersRequest(
        List<String> trackerIds
) {}
