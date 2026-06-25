package nl.vlettenrally.data.dto.team;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Request to assign a single tracker (from the game's pool) to a team, or to clear
 * it when {@code trackerId} is null/blank.
 */
@RegisterForReflection
public record AssignTrackerRequest(
        String trackerId
) {}
