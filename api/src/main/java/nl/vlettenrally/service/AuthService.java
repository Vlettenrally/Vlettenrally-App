package nl.vlettenrally.service;

import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for Authentication operations
 */
public interface AuthService {
    
    /**
     * Result of a team registration
     */
    record RegistrationResult(
        String registrationToken,
        String teamId,
        long expiresAt
    ) {}
    
    /**
     * Result of a token exchange
     */
    record TokenResult(
        String accessToken,
        String refreshToken,
        int expiresIn,
        String teamId,
        String gameId
    ) {}
    
    /**
     * Register a new team and generate a one-time registration token
     */
    RegistrationResult registerTeam(
        String gameId,
        String boatNumber,
        String scoutingGroup,
        String emergencyContact,
        java.util.List<String> participantNames,
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
     * Validate an access token and return the team ID
     */
    Optional<UUID> validateAccessToken(String accessToken);
    
    /**
     * Revoke all tokens for a team
     */
    void revokeTokens(UUID teamId);
}
