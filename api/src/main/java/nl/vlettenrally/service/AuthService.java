package nl.vlettenrally.service;

import io.smallrye.mutiny.Uni;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import nl.vlettenrally.data.domain.Admin;
import nl.vlettenrally.data.enums.Role;
import nl.vlettenrally.data.enums.TokenType;
import nl.vlettenrally.data.dto.auth.GameCheckInLinkResponse;
import nl.vlettenrally.data.domain.Team;
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
     * Select a team from a game-scoped registration token and receive access/refresh tokens
     */
    Optional<TokenResult> selectTeam(UUID gameId, UUID teamId);

    /**
     * Issue access + refresh tokens for a team without the check-in-token expiry check.
     * Used by the public multi-device check-in coordinator after a device has self-selected a team.
     *
     * When deviceId is non-null, the issued access token carries a `did` claim so that
     * the device's tokens can be revoked individually (admin "remove device" action).
     */
    TokenResult issueTeamTokens(Team team, UUID deviceId);

    /**
     * Convenience overload: issues tokens with no device binding (legacy WordPress path).
     */
    TokenResult issueTeamTokens(Team team);

    /**
     * Validate whether a game-scoped check-in token is still valid
     */
    boolean isCheckInTokenValid(UUID gameId);

    /**
     * Revoke all tokens for a team. Deletes persisted refresh tokens AND records a
     * revocation cutoff so that any access token whose `iat` predates the cutoff is
     * rejected at request time (see TeamTokenRevocationFilter).
     */
    void revokeTokens(UUID teamId);

    /**
     * Returns true if access tokens for the given team subject were revoked AFTER the
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
     * Returns true if the (subject, deviceId) pair was revoked AFTER the supplied
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
     * @param tokenType TokenType enum (ACCESS or CHECKIN)
     */
    String generateToken(String userId, String name, String role, Instant expiresAt, TokenType tokenType);

    /**
     * Generate a JWT token with multiple roles
     * @param tokenType TokenType enum (ACCESS or CHECKIN)
     */
    String generateToken(String userId, String name, Set<Role> roles, Instant expiresAt, TokenType tokenType);

    /**
     * Generate a game-scoped check-in token that allows teams to select themselves
     * Token expires at the earlier of: (1) 5 minutes after all teams check in, or (2) game start time
     *
     * @param gameId The ID of the game
     * @return GameCheckInLinkResponse containing the full URL and token details
     */
    GameCheckInLinkResponse generateGameCheckInLink(UUID gameId);
}
