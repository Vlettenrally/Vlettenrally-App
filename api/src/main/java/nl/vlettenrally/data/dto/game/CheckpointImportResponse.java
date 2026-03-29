package nl.vlettenrally.data.dto.game;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Response DTO for checkpoint import operation results.
 */
@RegisterForReflection
public record CheckpointImportResponse(
        int created,
        int updated,
        int errors
) {}

