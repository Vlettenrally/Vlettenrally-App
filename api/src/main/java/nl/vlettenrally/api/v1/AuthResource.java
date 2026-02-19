package nl.vlettenrally.api.v1;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.vlettenrally.data.dto.auth.AdminLoginRequest;
import nl.vlettenrally.data.dto.auth.AuthTokenResponse;
import nl.vlettenrally.data.dto.auth.MockLoginRequest;
import nl.vlettenrally.data.dto.auth.MockLoginResponse;
import nl.vlettenrally.data.dto.auth.MockUserResponse;
import nl.vlettenrally.data.dto.auth.RefreshTokenRequest;
import nl.vlettenrally.data.dto.auth.RegistrationResult;
import nl.vlettenrally.data.dto.auth.RegistrationTokenResponse;
import nl.vlettenrally.data.dto.auth.TeamRegistrationRequest;
import nl.vlettenrally.data.dto.auth.UpdatePasswordRequest;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.data.enums.TokenType;
import nl.vlettenrally.data.repository.AdminRepository;
import nl.vlettenrally.service.AuthService;
import nl.vlettenrally.service.TeamService;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * REST Resource for Authentication operations
 *
 * Endpoints:
 * - POST /api/v1/auth/register - Register a new team
 * - POST /api/v1/auth/refresh - Refresh access token
 * - POST /api/v1/auth/admin/login - Admin login with username/password
 * - POST /api/v1/auth/admin/update-password - Admin update password
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

    @ConfigProperty(name = "vlettenrally.jwt.access-token-expiry-minutes", defaultValue = "60")
    int accessTokenExpiryMinutes;

    @ConfigProperty(name = "vlettenrally.jwt.refresh-token-expiry-days", defaultValue = "5")
    int refreshTokenExpiryDays;

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
                ? List.of(registration.participantNames())
                : Collections.emptyList();

            RegistrationResult result = authService.registerTeam(
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
                .map(result -> Response.ok(new AuthTokenResponse(
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

            return authService.adminLogin(request.username(), request.password())
                .map(admin -> {
                    long accessTokenExpirySeconds = accessTokenExpiryMinutes * 60L;
                    long refreshTokenExpirySeconds = refreshTokenExpiryDays * 86400L;

                    Instant accessExpiresAt = Instant.now().plusSeconds(accessTokenExpirySeconds);
                    Instant refreshExpiresAt = Instant.now().plusSeconds(refreshTokenExpirySeconds);

                    // Use generateToken overload with Set<AdminRole> for multi-role support
                    String accessToken = authService.generateToken(
                        admin.getId().toString(),
                        admin.getUsername(),
                        admin.getRoles(),
                        accessExpiresAt,
                        TokenType.ACCESS
                    );

                    String refreshToken = authService.generateRefreshTokenValue();

                    // Store refresh token with Set of lowercase roles
                    Set<String> rolesForStorage = admin.getRoles().stream()
                        .map(role -> role.name().toLowerCase())
                        .collect(java.util.stream.Collectors.toSet());
                    authService.storeRefreshToken(refreshToken, admin.getId().toString(), admin.getUsername(), rolesForStorage, refreshExpiresAt.getEpochSecond());

                    return Response.ok(new AuthTokenResponse(
                        accessToken,
                        refreshToken,
                        accessTokenExpirySeconds
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
     * Admin update password
     * Allows an authenticated admin to change their password
     */
    @POST
    @Path("/admin/update-password")
    @RolesAllowed("admin")
    public Response updatePassword(
        UpdatePasswordRequest request,
        @jakarta.ws.rs.core.Context jakarta.ws.rs.core.SecurityContext securityContext
    ) {
        try {
            // Validate input
            if (request.currentPassword() == null || request.currentPassword().isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("currentPassword is required"))
                    .build();
            }
            if (request.newPassword() == null || request.newPassword().isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("newPassword is required"))
                    .build();
            }

            // Validate password strength
            if (request.newPassword().length() < 8) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("New password must be at least 8 characters long"))
                    .build();
            }

            // Get current admin from security context
            String adminId = securityContext.getUserPrincipal().getName();

            boolean success = authService.updateAdminPassword(
                UUID.fromString(adminId),
                request.currentPassword(),
                request.newPassword()
            );

            if (success) {
                return Response.ok().build();
            } else {
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ErrorResponse("Current password is incorrect or admin not found"))
                    .build();
            }

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Password update failed: " + e.getMessage()))
                .build();
        }
    }

    /**
     * Logout - revoke all tokens for the authenticated user
     */
    @POST
    @Path("/logout")
    @jakarta.annotation.security.RolesAllowed({"admin", "team"})
    public Response logout(@jakarta.ws.rs.core.Context jakarta.ws.rs.core.SecurityContext securityContext) {
        try {
            String userId = securityContext.getUserPrincipal().getName();
            authService.revokeTokens(UUID.fromString(userId));

            return Response.ok().build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Logout failed: " + e.getMessage()))
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
                    long accessTokenExpirySeconds = accessTokenExpiryMinutes * 60L;
                    Instant expiresAt = Instant.now().plusSeconds(accessTokenExpirySeconds);

                    String token = authService.generateToken(
                        teamId.toString(),
                        team.getName(),
                        "team",
                        expiresAt,
                        TokenType.ACCESS
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
