package nl.vlettenrally.api.v1;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.vlettenrally.data.Dto.*;
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
                Instant.ofEpochSecond(result.expiresAt())
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

    /**
     * Admin login with username and password
     * Returns a JWT token with role "admin"
     */
    @POST
    @Path("/admin/login")
    public Response adminLogin(AdminLoginRequestDto request) {
        try {
            if (request.username == null || request.username.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new AuthenticationErrorDto("username is required"))
                    .build();
            }
            if (request.password == null || request.password.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new AuthenticationErrorDto("password is required"))
                    .build();
            }

            return adminRepository.findByUsername(request.username)
                .filter(admin -> admin.isActive())
                .filter(admin -> BcryptUtil.matches(request.password, admin.getPasswordHash()))
                .map(admin -> { 
                    Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);
                    
                    String token = authService.generateMockToken(
                        admin.getId().toString(),
                        admin.getUsername(),
                        "admin",
                        expiresAt
                    );

                    return Response.ok(new AdminLoginResponseDto(
                        token,
                        admin.getId().toString(),
                        admin.getUsername(),
                        expiresAt.toString()
                    )).build();
                })
                .orElse(Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new AuthenticationErrorDto("Invalid username or password"))
                    .build());

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new AuthenticationErrorDto("Admin login failed: " + e.getMessage()))
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
                .entity(new AuthenticationErrorDto("Endpoint not available"))
                .build();
        }

        List<MockUserDto> mockUsers = teamService.findAll().stream()
            .map(team -> new MockUserDto(
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
    public Response mockLogin(MockLoginRequestDto request) {
        // Only allow in mock mode
        if (!mockEnabled) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new AuthenticationErrorDto("Endpoint not available"))
                .build();
        }

        try {
            if (request.userId == null || request.userId.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new AuthenticationErrorDto("userId is required"))
                    .build();
            }

            UUID teamId;
            try {
                teamId = UUID.fromString(request.userId);
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new AuthenticationErrorDto("Invalid userId format, expected UUID: " + request.userId))
                    .build();
            }

            return teamService.findById(teamId)
                .map(team -> {
                    Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);
                                      
                    String token = authService.generateMockToken(
                        request.userId,
                        team.getName(),
                        "team",
                        expiresAt
                    );

                    return Response.ok(new MockLoginResponseDto(token, expiresAt.toString())).build();
                })
                .orElse(Response.status(Response.Status.NOT_FOUND)
                    .entity(new AuthenticationErrorDto("Team not found: " + request.userId))
                    .build());

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new AuthenticationErrorDto("Mock login failed: " + e.getMessage()))
                .build();
        }
    }

    // DTO for refresh token request
    public static class RefreshTokenRequestDto {
        public String refreshToken;
    }

    // DTO for mock login request
    public static class MockLoginRequestDto {
        public String userId;
    }

    // DTO for mock login response
    public static class MockLoginResponseDto {
        public String token;
        public String expiresAt;

        public MockLoginResponseDto(String token, String expiresAt) {
            this.token = token;
            this.expiresAt = expiresAt;
        }
    }

    // DTO for mock user listing
    public static class MockUserDto {
        public String id;
        public String name;
        public String role;
        public String gameId;

        public MockUserDto(String id, String name, String role, String gameId) {
            this.id = id;
            this.name = name;
            this.role = role;
            this.gameId = gameId;
        }
    }

    // DTO for admin login request
    public static class AdminLoginRequestDto {
        public String username;
        public String password;
    }

    // DTO for admin login response
    public static class AdminLoginResponseDto {
        public String token;
        public String adminId;
        public String username;
        public String expiresAt;

        public AdminLoginResponseDto(String token, String adminId, String username, String expiresAt) {
            this.token = token;
            this.adminId = adminId;
            this.username = username;
            this.expiresAt = expiresAt;
        }
    }
}
