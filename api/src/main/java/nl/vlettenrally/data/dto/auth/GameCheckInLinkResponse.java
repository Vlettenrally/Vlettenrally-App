package nl.vlettenrally.data.dto.auth;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Response containing game-scoped check-in link details.
 * 
 * Token expiry rules:
 * - Expires at the earlier of: (1) 5 minutes after all teams check in, or (2) game start time
 * 
 * @param url Full check-in URL with token (e.g., https://app.example.com/check-in?token=xyz)
 * @param token Registration token for the game (game-scoped, allows team selection)
 * @param expiresAt Epoch second when the token expires
 * @param expiryReason Human-readable reason for expiry (e.g., "Game starts at 14:30" or "5 minutes after all teams check in")
 */
@RegisterForReflection
public record GameCheckInLinkResponse(
    String url,
    String token,
    long expiresAt,
    String expiryReason
) {}
