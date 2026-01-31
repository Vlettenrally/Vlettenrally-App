package nl.vlettenrally.data.dto.game;

/**
 * Response DTO for team import operation results.
 */
public record TeamImportResponse(
        int created,
        int updated,
        int errors
) {}
