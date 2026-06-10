package nl.vlettenrally.data.dto.game;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Response DTO for linking teams to tracker participants:
 * how many teams got their externalParticipantId linked and how many
 * tracker participants could not be matched to a team.
 */
@RegisterForReflection
public record ParticipantLinkResponse(
        int linked,
        int unmatched
) {}
