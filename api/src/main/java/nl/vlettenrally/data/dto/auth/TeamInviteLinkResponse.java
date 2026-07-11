package nl.vlettenrally.data.dto.auth;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Response containing a team-scoped invite link that lets a teammate's phone join
 * an already-checked-in team directly (skipping the game/team pickers) and bypass
 * the digital check-in time gate.
 * <p>
 * A fresh token is minted on every request (each time the team opens its QR page
 * or an admin renders the QR), so the embedded token may carry a generous expiry.
 *
 * @param url   Full check-in URL with the invite token (e.g. https://app/check-in?invite=xyz)
 * @param token The signed invite token itself
 */
@RegisterForReflection
public record TeamInviteLinkResponse(
    String url,
    String token
) {}
