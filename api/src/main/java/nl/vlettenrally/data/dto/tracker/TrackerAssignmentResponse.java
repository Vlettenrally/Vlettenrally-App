package nl.vlettenrally.data.dto.tracker;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * One team — game pair holding a tracker. A tracker may be assigned in
 * multiple non-completed games at once (per-game uniqueness).
 */
@RegisterForReflection
public record TrackerAssignmentResponse(
        String teamName,
        String gameName
) {}
