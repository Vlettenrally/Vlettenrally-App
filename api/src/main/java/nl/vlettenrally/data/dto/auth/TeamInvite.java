package nl.vlettenrally.data.dto.auth;

import java.util.UUID;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Decoded contents of a validated team invite: the game and team the invite grants access
 * to. Produced by {@code AuthService.validateTeamInviteToken}, and also the value
 * {@code InviteCodeStore} serializes into Valkey under the invite code.
 */
@RegisterForReflection
public record TeamInvite(
    UUID gameId,
    UUID teamId
) {}
