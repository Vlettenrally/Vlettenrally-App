package nl.vlettenrally.data.dto.game;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Response DTO for team import operation results.
 */
@RegisterForReflection
public record TeamImportResponse(
        int created,
        int updated,
        int errors
) {}
