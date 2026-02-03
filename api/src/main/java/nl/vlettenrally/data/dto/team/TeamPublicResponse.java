package nl.vlettenrally.data.dto.team;

import java.util.UUID;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Minimal DTO for public team views (rivals, rankings).
 * Contains only non-sensitive public information.
 */
@RegisterForReflection
public record TeamPublicResponse(
    UUID id,
    String name,
    String boatNumber,
    int totalPoints
) {}
