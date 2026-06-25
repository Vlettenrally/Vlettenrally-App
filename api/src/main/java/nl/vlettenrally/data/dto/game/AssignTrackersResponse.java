package nl.vlettenrally.data.dto.game;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * Response DTO for bulk tracker assignment results. Shaped to drive the same
 * clickable-chip UI as the team import.
 */
@RegisterForReflection
public record AssignTrackersResponse(
        int assigned,
        int teamsWithoutTracker,
        int unused,
        List<String> assignedPairs,
        List<String> teamsWithoutTrackerNames,
        List<String> unusedTrackers
) {}
