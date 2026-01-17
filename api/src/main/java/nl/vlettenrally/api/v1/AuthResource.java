package nl.vlettenrally.api.v1;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.vlettenrally.api.v1.Dto.*;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;

/**
 * Authentication Resource
 * Handles team registration from third party and token exchange for player app
 */
@Path("/api/v1/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    // In-memory storage for registration tokens (use Redis or database in production)
    private static final ConcurrentHashMap<String, RegistrationTokenData> registrationTokens = new ConcurrentHashMap<>();
    
    private static class RegistrationTokenData {
        String teamId;
        long expiresAt;
        boolean used;
        
        RegistrationTokenData(String teamId, long expiresAt) {
            this.teamId = teamId;
            this.expiresAt = expiresAt;
            this.used = false;
        }
    }

    /**
     * Register a new team from third party registration system
     * Returns a one-time registration token
     * 
     * POST /api/v1/auth/register
     */
    @POST
    @Path("/register")
    public Response registerTeam(TeamRegistrationDto registration) {
        try {
            // Validate input
            if (registration.gameId == null || registration.boatNumber == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new AuthenticationErrorDto("gameId and boatNumber are required"))
                    .build();
            }

            // TODO: Create team in database
            // For now, generate a mock team ID
            String teamId = UUID.randomUUID().toString();
            
            // TODO: Save team data to database with provided information
            // Team team = new Team();
            // team.setId(teamId);
            // team.setGameId(registration.gameId);
            // team.setBoatNumber(registration.boatNumber);
            // team.setScoutingGroup(registration.scoutingGroup);
            // team.setEmergencyContact(registration.emergencyContact);
            // team.setParticipantNames(registration.participantNames);
            // team.setBookingId(registration.bookingId);
            // teamRepository.persist(team);

            // Generate one-time registration token (valid for 15 minutes)
            String registrationToken = UUID.randomUUID().toString();
            long expiresAt = Instant.now().plusSeconds(900).getEpochSecond(); // 15 minutes
            
            registrationTokens.put(registrationToken, new RegistrationTokenData(teamId, expiresAt));

            // Clean up expired tokens (basic cleanup)
            cleanupExpiredTokens();

            return Response.ok(new RegistrationTokenDto(registrationToken, teamId, expiresAt))
                .build();
                
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new AuthenticationErrorDto("Registration failed: " + e.getMessage()))
                .build();
        }
    }

    /**
     * Exchange registration token for access + refresh tokens
     * This is called by the player app after receiving the registration token
     * 
     * POST /api/v1/auth/token
     */
    @POST
    @Path("/token")
    public Response exchangeToken(TokenExchangeRequestDto request) {
        try {
            if (request.registrationToken == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new AuthenticationErrorDto("registrationToken is required"))
                    .build();
            }

            // Validate registration token
            RegistrationTokenData tokenData = registrationTokens.get(request.registrationToken);
            
            if (tokenData == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new AuthenticationErrorDto("Invalid registration token"))
                    .build();
            }

            if (tokenData.used) {
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new AuthenticationErrorDto("Registration token has already been used"))
                    .build();
            }

            if (Instant.now().getEpochSecond() > tokenData.expiresAt) {
                registrationTokens.remove(request.registrationToken);
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new AuthenticationErrorDto("Registration token has expired"))
                    .build();
            }

            // Mark token as used (one-time use only)
            tokenData.used = true;

            // TODO: Generate JWT access token (valid for 1 hour)
            String accessToken = generateAccessToken(tokenData.teamId);
            
            // TODO: Generate refresh token (valid for 7 days)
            String refreshToken = generateRefreshToken(tokenData.teamId);
            
            // Remove used registration token
            registrationTokens.remove(request.registrationToken);

            return Response.ok(new TokenResponseDto(accessToken, refreshToken, 3600))
                .build();
                
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new AuthenticationErrorDto("Token exchange failed: " + e.getMessage()))
                .build();
        }
    }

    /**
     * Refresh access token using refresh token
     * 
     * POST /api/v1/auth/refresh
     */
    @POST
    @Path("/refresh")
    public Response refreshToken(RefreshTokenRequestDto request) {
        try {
            if (request.refreshToken == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new AuthenticationErrorDto("refreshToken is required"))
                    .build();
            }

            // TODO: Validate refresh token and extract team ID
            String teamId = validateRefreshToken(request.refreshToken);
            
            if (teamId == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new AuthenticationErrorDto("Invalid or expired refresh token"))
                    .build();
            }

            // Generate new access token
            String accessToken = generateAccessToken(teamId);
            
            return Response.ok(new TokenResponseDto(accessToken, request.refreshToken, 3600))
                .build();
                
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new AuthenticationErrorDto("Token refresh failed: " + e.getMessage()))
                .build();
        }
    }

    // Helper methods

    private String generateAccessToken(String teamId) {
        // TODO: Implement JWT token generation with SmallRye JWT
        // For now, return a mock token with teamId embedded
        return "access_" + teamId + "_" + System.currentTimeMillis();
    }

    private String generateRefreshToken(String teamId) {
        // TODO: Store refresh token in database with expiry
        return "refresh_" + teamId + "_" + UUID.randomUUID().toString();
    }

    private String validateRefreshToken(String refreshToken) {
        // TODO: Validate refresh token from database
        // Extract and return teamId if valid, null otherwise
        if (refreshToken.startsWith("refresh_")) {
            String[] parts = refreshToken.split("_");
            if (parts.length >= 2) {
                return parts[1]; // Return teamId
            }
        }
        return null;
    }

    private void cleanupExpiredTokens() {
        long now = Instant.now().getEpochSecond();
        registrationTokens.entrySet().removeIf(entry -> entry.getValue().expiresAt < now);
    }

    private static class RefreshTokenRequestDto {
        public String refreshToken;
    }
}
