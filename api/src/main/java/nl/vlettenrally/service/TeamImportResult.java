package nl.vlettenrally.service;

import java.util.List;

/**
 * Result of a team import operation.
 *
 * @param skipped           number of attendees skipped because their boat number was
 *                          already imported for this game (duplicate registrations)
 * @param createdTeams      boat numbers of newly created teams
 * @param updatedTeams      boat numbers of updated teams
 * @param skippedDuplicates boat numbers skipped as duplicates
 * @param errorDetails      per-attendee error descriptions for failed imports
 */
public record TeamImportResult(
        int created,
        int updated,
        int skipped,
        int errors,
        List<String> createdTeams,
        List<String> updatedTeams,
        List<String> skippedDuplicates,
        List<String> errorDetails
) {}
