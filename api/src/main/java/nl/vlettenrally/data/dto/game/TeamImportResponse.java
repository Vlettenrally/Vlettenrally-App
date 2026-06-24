package nl.vlettenrally.data.dto.game;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * Response DTO for team import operation results.
 *
 * @param skipped           number of attendees skipped as duplicate boat numbers
 * @param createdTeams      boat numbers of newly created teams
 * @param updatedTeams      boat numbers of updated teams
 * @param skippedDuplicates boat numbers skipped as duplicates
 * @param errorDetails      per-attendee error descriptions for failed imports
 */
@RegisterForReflection
public record TeamImportResponse(
        int created,
        int updated,
        int skipped,
        int errors,
        List<String> createdTeams,
        List<String> updatedTeams,
        List<String> skippedDuplicates,
        List<String> errorDetails
) {}
