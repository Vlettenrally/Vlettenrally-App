package nl.vlettenrally.service;

/**
 * Result of linking teams to tracker participants: how many teams got their
 * externalParticipantId linked and how many tracker participants could not be
 * matched to a team.
 */
public record ParticipantLinkResult(
        int linked,
        int unmatched
) {}
