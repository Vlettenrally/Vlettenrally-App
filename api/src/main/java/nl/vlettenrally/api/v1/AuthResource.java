package nl.vlettenrally.api.v1;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.vlettenrally.data.dto.auth.*;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.data.repository.AdminRepository;
import nl.vlettenrally.service.AuthService;
import nl.vlettenrally.service.TeamService;

import io.quarkus.elytron.security.common.BcryptUtil;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * REST Resource for Authentication operations
 * 
 * Endpoints:
 * - POST /api/v1/auth/register - Register a new team
 * - POST /api/v1/auth/token - Exchange registration token for access tokens
 * - POST /api/v1/auth/refresh - Refresh access token
 * - POST /api/v1/auth/admin/login - Admin login with username/password
 * - POST /api/v1/auth/mock-login - Development only: get token for mock user
 * - GET /api/v1/auth/mock-users - Development only: list available mock users
 */
@Path("/api/v1/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService authService;

    @Inject
    TeamService teamService;

    @Inject
    AdminRepository adminRepository;

    @ConfigProperty(name = "vlettenrally.mock.enabled", defaultValue = "false")
    boolean mockEnabled;

    /**
     * Register a new team from third party registration system
     * Returns a one-time registration token
     */
    @POST
    @Path("/register")
    public Response registerTeam(TeamRegistrationRequest registration) {
        try {
            // Validate input
            if (registration.gameId() == null || registration.boatNumber() == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("gameId and boatNumber are required"))
                    .build();
            }

            // Convert array to list
            List<String> participants = registration.participantNames() != null 
                ? Arrays.asList(registration.participantNames()) 
                : Collections.emptyList();

            AuthService.RegistrationResult result = authService.registerTeam(
                registration.gameId(),
                registration.boatNumber(),
                registration.scoutingGroup(),
                registration.emergencyContact(),
                participants,
                registration.bookingId()
            );

            return Response.ok(new RegistrationTokenResponse(
                result.registrationToken(),
                result.teamId(),
                Instant.ofEpochSecond(result.expiresAt())
            )).build();

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Registration failed: " + e.getMessage()))
                .build();
        }
    }

    /**
     * Exchange registration token for access + refresh tokens
     * This is called by the player app after receiving the registration token
     */
    @POST
    @Path("/token")
    public Response exchangeToken(TokenExchangeRequest request) {
        try {
            if (request.registrationToken() == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("registrationToken is required"))
                    .build();
            }

            return authService.exchangeToken(request.registrationToken())
                .map(result -> Response.ok(new TokenResponse(
                    result.accessToken(),
                    result.refreshToken(),
                    result.expiresIn()
                )).build())
                .orElse(Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ErrorResponse("Invalid, expired, or already used registration token"))
                    .build());

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Token exchange failed: " + e.getMessage()))
                .build();
        }
    }

    /**
     * Refresh access token using refresh token
     */
    @POST
    @Path("/refresh")
    public Response refreshToken(RefreshTokenRequest request) {
        try {
            if (request.refreshToken() == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("refreshToken is required"))
                    .build();
            }

            return authService.refreshToken(request.refreshToken())
                .map(result -> Response.ok(new TokenResponse(
                    result.accessToken(),
                    result.refreshToken(),
                    result.expiresIn()
                )).build())
                .orElse(Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ErrorResponse("Invalid or expired refresh token"))
                    .build());

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Token refresh failed: " + e.getMessage()))
                .build();
        }
    }

    /**
     * Admin login with username and password
     * Returns a JWT token with role "admin"
     */
    @POST
    @Path("/admin/login")
    public Response adminLogin(AdminLoginRequest request) {
        try {
            if (request.username() == null || request.username().isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("username is required"))
                    .build();
            }
            if (request.password() == null || request.password().isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("password is required"))
                    .build();
            }

            return adminRepository.findByUsername(request.username())
                .filter(admin -> admin.isActive())
                .filter(admin -> BcryptUtil.matches(request.password(), admin.getPasswordHash()))
                .map(admin -> { 
                    Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);
                    
                    String token = authService.generateMockToken(
                        admin.getId().toString(),
                        admin.getUsername(),
                        "admin",
                        expiresAt
                    );

                    return Response.ok(new AdminLoginResponse(
                        token,
                        admin.getId().toString(),
                        admin.getUsername(),
                        expiresAt.toString()
                    )).build();
                })
                .orElse(Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ErrorResponse("Invalid username or password"))
                    .build());

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Admin login failed: " + e.getMessage()))
                .build();
        }
    }

    /**
     * Development only: List available mock users (teams from repository)
     */
    @GET
    @Path("/mock-users")
    public Response listMockUsers() {
        if (!mockEnabled) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Endpoint not available"))
                .build();
        }

        List<MockUserResponse> mockUsers = teamService.findAll().stream()
            .map(team -> new MockUserResponse(
                team.getId().toString(),
                team.getName(),
                "team",
                team.getGameId() != null ? team.getGameId().toString() : null
            ))
            .toList();

        return Response.ok(mockUsers).build();
    }

    /**
     * Development only: Generate a token for a mock user
     * This endpoint should only be enabled when mocks are active
     */
    @POST
    @Path("/mock-login")
    public Response mockLogin(MockLoginRequest request) {
        // Only allow in mock mode
        if (!mockEnabled) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Endpoint not available"))
                .build();
        }

        try {
            if (request.userId() == null || request.userId().isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("userId is required"))
                    .build();
            }

            UUID teamId;
            try {
                teamId = UUID.fromString(request.userId());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Invalid userId format, expected UUID: " + request.userId()))
                    .build();
            }

            return teamService.findById(teamId)
                .map(team -> {
                    Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);
                                      
                    String token = authService.generateMockToken(
                        request.userId(),
                        team.getName(),
                        "team",
                        expiresAt
                    );

                    return Response.ok(new MockLoginResponse(token, expiresAt.toString())).build();
                })
                .orElse(Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Team not found: " + request.userId()))
                    .build());

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Mock login failed: " + e.getMessage()))
                .build();
        }
    }
}
