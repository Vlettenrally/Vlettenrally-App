package nl.vlettenrally.api.v1;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.vlettenrally.api.v1.Dto.*;
import nl.vlettenrally.service.AuthService;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * REST Resource for Authentication operations
 * 
 * Endpoints:
 * - POST /api/v1/auth/register - Register a new team
 * - POST /api/v1/auth/token - Exchange registration token for access tokens
 * - POST /api/v1/auth/refresh - Refresh access token
 */
@Path("/api/v1/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService authService;

    /**
     * Register a new team from third party registration system
     * Returns a one-time registration token
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

            // Convert array to list
            List<String> participants = registration.participantNames != null 
                ? Arrays.asList(registration.participantNames) 
                : Collections.emptyList();

            AuthService.RegistrationResult result = authService.registerTeam(
                registration.gameId,
                registration.boatNumber,
                registration.scoutingGroup,
                registration.emergencyContact,
                participants,
                registration.bookingId
            );

            return Response.ok(new RegistrationTokenDto(
                result.registrationToken(),
                result.teamId(),
                result.expiresAt()
            )).build();

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new AuthenticationErrorDto("Registration failed: " + e.getMessage()))
                .build();
        }
    }

    /**
     * Exchange registration token for access + refresh tokens
     * This is called by the player app after receiving the registration token
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

            return authService.exchangeToken(request.registrationToken)
                .map(result -> Response.ok(new TokenResponseDto(
                    result.accessToken(),
                    result.refreshToken(),
                    result.expiresIn()
                )).build())
                .orElse(Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new AuthenticationErrorDto("Invalid, expired, or already used registration token"))
                    .build());

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new AuthenticationErrorDto("Token exchange failed: " + e.getMessage()))
                .build();
        }
    }

    /**
     * Refresh access token using refresh token
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

            return authService.refreshToken(request.refreshToken)
                .map(result -> Response.ok(new TokenResponseDto(
                    result.accessToken(),
                    result.refreshToken(),
                    result.expiresIn()
                )).build())
                .orElse(Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new AuthenticationErrorDto("Invalid or expired refresh token"))
                    .build());

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new AuthenticationErrorDto("Token refresh failed: " + e.getMessage()))
                .build();
        }
    }

    // DTO for refresh token request
    public static class RefreshTokenRequestDto {
        public String refreshToken;
    }
}
