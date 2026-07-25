package nl.vlettenrally.service;

import io.smallrye.mutiny.Uni;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import nl.vlettenrally.data.domain.Admin;
import nl.vlettenrally.data.enums.Role;
import nl.vlettenrally.data.enums.TokenType;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.dto.auth.TeamInvite;
import nl.vlettenrally.data.dto.auth.TeamInviteLinkResponse;
import nl.vlettenrally.data.dto.auth.TokenResult;

/**
 * Service interface for Authentication operations
 */
public interface AuthService {

    /**
     * Exchange a registration token for access and refresh tokens
     */
    Optional<TokenResult> exchangeToken(String registrationToken);

    /**
     * Refresh an access token using a refresh token
     */
    Optional<TokenResult> refreshToken(String refreshToken);

    /**
     * Issue access + refresh tokens for a team without the check-in-token expiry check.
     * Used by the public multi-device check-in coordinator after a device has self-selected a team.
     * <p>
     * When deviceId is non-null, the issued access token carries a `did` claim so that
     * the device's tokens can be revoked individually (admin "remove device" action).
     */
    TokenResult issueTeamTokens(Team team, UUID deviceId);

    /**
     * Convenience overload: issues tokens with no device binding.
     */
    TokenResult issueTeamTokens(Team team);

    /**
     * Revoke all tokens for a team. Deletes persisted refresh tokens AND records a
     * revocation cutoff so that any access token whose `iat` predates the cutoff is
     * rejected at request time (see TeamTokenRevocationFilter).
     */
    void revokeTokens(UUID teamId);

    /**
     * Returns Uni<true></true> if access tokens for the given team subject were revoked AFTER the
     * supplied issued-at instant — i.e. the access token must be rejected.
     */
    Uni<Boolean> isAccessTokenRevoked(String subject, Instant issuedAt);

    /**
     * Revoke tokens for a single device of a team (used when an admin removes a single
     * device rather than resetting the whole team). The team's other devices remain
     * valid. Refresh tokens are not deleted — they are subject-keyed, not device-keyed.
     */
    void revokeDeviceTokens(UUID teamId, UUID deviceId);

    /**
     * Returns Uni<true></true> if the (subject, deviceId) pair was revoked AFTER the supplied
     * issued-at instant.
     */
    Uni<Boolean> isDeviceTokenRevoked(String subject, UUID deviceId, Instant issuedAt);

    /**
     * Store a refresh token for validation and rotation
     */
    void storeRefreshToken(String refreshToken, String subject, String name, Set<String> roles, long expiresAt);

    /**
     * Authenticate admin user with username and password
     * @param username Admin username
     * @param password Admin password
     * @return Optional containing Admin if authentication successful
     */
    Optional<Admin> adminLogin(String username, String password);

    /**
     * Update admin password
     * @param adminId Admin ID
     * @param currentPassword Current password for verification
     * @param newPassword New password to set
     * @return true if password updated successfully, false otherwise
     */
    boolean updateAdminPassword(UUID adminId, String currentPassword, String newPassword);

    /**
     * Generate a secure opaque refresh token string for storage in the database.
     */
    String generateRefreshTokenValue();

    /**
     * Generate a JWT token with a single role
     * @param tokenType TokenType enum (e.g. ACCESS)
     */
    String generateToken(String userId, String name, String role, Instant expiresAt, TokenType tokenType);

    /**
     * Generate a JWT token with multiple roles
     * @param tokenType TokenType enum (e.g. ACCESS)
     */
    String generateToken(String userId, String name, Set<Role> roles, Instant expiresAt, TokenType tokenType);

    /**
     * Mint a team-scoped invite code + URL. Scanning the resulting QR lets a teammate's
     * phone join this team directly (skipping the game/team pickers) and bypass the
     * digital check-in time gate.
     * <p>
     * The code is short and short-lived (see {@code InviteCodeStore}). Calling this again
     * for a team that already has a live code returns that same code with its TTL slid
     * forward, so the QR on screen stays stable — but the caller displaying it must keep
     * calling within {@code expiresInSeconds} or the code on screen goes dead.
     *
     * @param teamId The ID of the team the invite grants access to
     * @return TeamInviteLinkResponse with the shareable URL, the QR-optimised URL, and the code
     */
    TeamInviteLinkResponse generateTeamInviteToken(UUID teamId);

    /**
     * Resolve a team invite code to the game + team it grants access to, sliding its TTL so
     * the rest of the check-in flow has a full window to complete. Does not consume the code:
     * one QR is scanned by several teammates.
     *
     * @param token The invite code from the QR / invite URL (case-insensitive)
     * @return the decoded {@link TeamInvite}, or empty if the code is missing/invalid/expired
     */
    Optional<TeamInvite> validateTeamInviteToken(String token);
}
