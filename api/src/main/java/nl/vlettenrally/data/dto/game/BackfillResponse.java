package nl.vlettenrally.data.dto.game;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Response DTO for a per-team historical-track backfill.
 *
 * @param inserted                 number of previously-missing fixes inserted
 * @param droppedByGuard           number of fetched fixes dropped by the outlier guard
 * @param checkpointPassesDetected number of checkpoint passes newly credited from the backfilled gap
 */
@RegisterForReflection
public record BackfillResponse(
        int inserted,
        int droppedByGuard,
        int checkpointPassesDetected
) {}
