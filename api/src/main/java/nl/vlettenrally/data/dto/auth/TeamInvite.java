package nl.vlettenrally.data.dto.auth;

import java.util.UUID;

/**
 * Decoded contents of a validated team invite token: the game and team the invite
 * grants access to. Produced by {@code AuthService.validateTeamInviteToken}.
 */
public record TeamInvite(
    UUID gameId,
    UUID teamId
) {}
