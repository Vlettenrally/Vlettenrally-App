package nl.vlettenrally.data.dto.auth;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.UUID;

/**
 * Public response for resolving a scanned invite token: the game + team it grants
 * access to, plus the team name so the check-in flow can show which team is being
 * joined on the nickname step.
 */
@RegisterForReflection
public record TeamInviteResolveResponse(
    UUID gameId,
    UUID teamId,
    String teamName
) {}
