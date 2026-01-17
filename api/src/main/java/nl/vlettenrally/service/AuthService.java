package nl.vlettenrally.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for Authentication operations
 */
public interface AuthService {

    // Record types for results
    record RegistrationResult(String registrationToken, String teamId, long expiresAt) {}
    record TokenResult(String accessToken, String refreshToken, long expiresIn, String teamId, String gameId) {}

    /**
     * Register a new team and return a one-time registration token
     */
    RegistrationResult registerTeam(
            String gameId,
            String boatNumber,
            String scoutingGroup,
            String emergencyContact,
            List<String> participantNames,
            String bookingId
    );

    /**
     * Exchange a registration token for access and refresh tokens
     */
    Optional<TokenResult> exchangeToken(String registrationToken);

    /**
     * Refresh an access token using a refresh token
     */
    Optional<TokenResult> refreshToken(String refreshToken);

    /**
     * Validate an access token and return the team ID if valid
     */
    Optional<UUID> validateAccessToken(String accessToken);

    /**
     * Revoke all tokens for a team
     */
    void revokeTokens(UUID teamId);

    /**
     * Generate a JWT token for mock/development login
     */
    String generateMockToken(String userId, String name, String role, Instant expiresAt);
}
