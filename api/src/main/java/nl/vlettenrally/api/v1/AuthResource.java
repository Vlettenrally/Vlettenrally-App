package nl.vlettenrally.api.v1;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.vlettenrally.api.security.SecurityRoles;
import nl.vlettenrally.data.dto.auth.AdminLoginRequest;
import nl.vlettenrally.data.dto.auth.AuthTokenResponse;
import nl.vlettenrally.data.dto.auth.RefreshOutcome;
import nl.vlettenrally.data.dto.auth.RefreshTokenRequest;
import nl.vlettenrally.data.dto.auth.UpdatePasswordRequest;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.data.enums.TokenType;
import nl.vlettenrally.service.AuthService;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * REST Resource for Authentication operations
 * Endpoints:
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

    /** The caller's access token, for the `did` claim that says which device is logging out. */
    @Inject
    JsonWebToken jwt;

    @ConfigProperty(name = "vlettenrally.jwt.access-token-expiry-minutes", defaultValue = "60")
    int accessTokenExpiryMinutes;

    @ConfigProperty(name = "vlettenrally.jwt.refresh-token-expiry-days", defaultValue = "7")
    int refreshTokenExpiryDays;

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

            // Retryable and Rejected must not collapse into the same status: a 401 ends the
            // session on every client, so a duplicate in-flight refresh used to log a team out.
            // 503 is what an unmodified PWA already treats as transient — it keeps its tokens.
            return switch (authService.refreshToken(request.refreshToken())) {
                case RefreshOutcome.Success success -> Response.ok(new AuthTokenResponse(
                    success.tokens().accessToken(),
                    success.tokens().refreshToken(),
                    success.tokens().expiresIn()
                )).build();
                case RefreshOutcome.Retryable retryable -> Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .header("Retry-After", 2)
                    .entity(new ErrorResponse("Token refresh temporarily unavailable, please retry"))
                    .build();
                case RefreshOutcome.Rejected rejected -> Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ErrorResponse("Invalid or expired refresh token"))
                    .build();
            };

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
    @RolesAllowed(SecurityRoles.STAFF)
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
     * Logout - revoke the caller's tokens.
     * <p>
     * Scoped to the device that is logging out, not to the subject. A team's devices all share one
     * subject (the team id), so revoking the subject here logged out every phone in the crew — and
     * their only way back in is a physical check-in at the rally desk. One person leaving the app
     * must not strand the boat.
     * <p>
     * Falls back to a subject-wide revoke when the token carries no `did` claim: admins, who have
     * no device binding and can simply log in again with a password, and team tokens issued before
     * device binding existed (which disappear within one refresh-token lifetime).
     */
    @POST
    @Path("/logout")
    @jakarta.annotation.security.RolesAllowed({SecurityRoles.STAFF, SecurityRoles.TEAM})
    public Response logout(@jakarta.ws.rs.core.Context jakarta.ws.rs.core.SecurityContext securityContext) {
        try {
            UUID userId = UUID.fromString(securityContext.getUserPrincipal().getName());
            UUID deviceId = callerDeviceId();

            if (deviceId != null) {
                authService.revokeDeviceTokens(userId, deviceId);
            } else {
                authService.revokeTokens(userId);
            }

            return Response.ok().build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Logout failed: " + e.getMessage()))
                .build();
        }
    }

    /** The device the caller's access token is bound to, or null if it carries no `did` claim. */
    private UUID callerDeviceId() {
        if (jwt == null) {
            return null;
        }
        Object did = jwt.getClaim("did");
        if (did == null) {
            return null;
        }
        try {
            return UUID.fromString(did.toString());
        } catch (IllegalArgumentException e) {
            // Malformed claim: fall back to the subject-wide revoke rather than silently
            // revoking nothing — a logout that revokes nothing is worse than one that overreaches.
            return null;
        }
    }
}
