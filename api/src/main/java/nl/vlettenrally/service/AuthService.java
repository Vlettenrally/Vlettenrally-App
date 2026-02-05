package nl.vlettenrally.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import nl.vlettenrally.data.domain.AdminRole;
import nl.vlettenrally.data.dto.auth.RegistrationResult;
import nl.vlettenrally.data.dto.auth.TokenResult;

/**
 * Service interface for Authentication operations
 */
public interface AuthService {

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
     * Revoke all tokens for a team
     */
    void revokeTokens(UUID teamId);

    /**
     * Store a refresh token for validation and rotation
     */
    void storeRefreshToken(String refreshToken, String subject, String name, String role, long expiresAt);

    /**
     * Generate a JWT token for mock/development login
     */
    String generateMockToken(String userId, String name, String role, Instant expiresAt);

    /**
     * Generate a JWT token for admin login with multiple roles
     */
    String generateAdminToken(String userId, String name, Set<AdminRole> roles, Instant expiresAt);
}
