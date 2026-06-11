package nl.vlettenrally.data.dto.game;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * Outcome summary of a copy-zones operation.
 *
 * @param copied           number of zones created in the target game
 * @param skippedDuplicate names of source zones skipped because a zone with that
 *                         name already exists in the target game
 */
@RegisterForReflection
public record CopyCheckpointsResult(
    int copied,
    List<String> skippedDuplicate
) {}
