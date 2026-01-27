package nl.vlettenrally.data.dto.team;

import java.util.UUID;

/**
 * Minimal DTO for public team views (rivals, rankings).
 * Contains only non-sensitive public information.
 */
public record TeamPublicResponse(
    UUID id,
    String name,
    String boatNumber,
    int totalPoints
) {}
