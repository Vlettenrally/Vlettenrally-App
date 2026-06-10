package nl.vlettenrally.data.dto.game;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Response DTO for checkpoint (zone) import from the location tracker:
 * how many checkpoints were created, updated, or errored.
 */
@RegisterForReflection
public record CheckpointImportResponse(
        int created,
        int updated,
        int errors
) {}
