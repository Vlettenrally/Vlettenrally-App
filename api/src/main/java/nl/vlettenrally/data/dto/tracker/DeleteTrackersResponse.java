package nl.vlettenrally.data.dto.tracker;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * Result of bulk-deleting tracker IDs: how many were removed, how many were not
 * in the pool, and the teams left without a tracker because theirs was deleted.
 */
@RegisterForReflection
public record DeleteTrackersResponse(
        int deleted,
        int notFound,
        List<String> unassignedTeams
) {}
