package nl.vlettenrally.data.dto.auth;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Response containing a team-scoped invite link that lets a teammate's phone join
 * an already-checked-in team directly (skipping the game/team pickers) and bypass
 * the digital check-in time gate.
 * <p>
 * The link carries a short opaque code (see {@code InviteCodeStore}), not a token: the whole
 * point is a QR small enough to scan reliably. The code is short-lived and its TTL slides on
 * every use, so whoever is displaying the QR must re-request it within {@code expiresInSeconds}
 * to keep the code on screen alive.
 *
 * @param url              Shareable check-in URL, e.g. {@code https://app/i/K7QM4X9BTD}
 * @param qrUrl            The same URL uppercased — this, not {@code url}, is what the QR should
 *                         encode. Being uppercase and free of {@code ?} and {@code =} lets the QR
 *                         use alphanumeric mode, which is what keeps the symbol small.
 * @param token            The bare invite code
 * @param expiresInSeconds Remaining lifetime of the code, so the caller can schedule a refresh
 */
@RegisterForReflection
public record TeamInviteLinkResponse(
    String url,
    String qrUrl,
    String token,
    long expiresInSeconds
) {}
