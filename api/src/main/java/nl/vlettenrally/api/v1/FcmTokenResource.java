package nl.vlettenrally.api.v1;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.data.dto.device.FcmTokenRequest;
import nl.vlettenrally.data.dto.device.FcmTokenResponse;
import nl.vlettenrally.service.FcmTokenService;

import java.util.UUID;

import io.quarkus.logging.Log;

/**
 * REST Resource for FCM Token operations
 * 
 * Endpoints:
 * - POST /api/v1/fcm-tokens - Register an FCM token for the authenticated user (team or admin)
 * - DELETE /api/v1/fcm-tokens/{token} - Unregister an FCM token
 */
@Path("/api/v1/fcm-tokens")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"team", "admin"})
public class FcmTokenResource {

    @Inject
    FcmTokenService fcmTokenService;

    /**
     * Register an FCM token for push notifications.
     * Creates or updates the device registration and FCM token for the authenticated user (team or admin).
     */
    @POST
    public Response registerFcmToken(
            FcmTokenRequest request,
            @Context SecurityContext securityContext) {
        
        if (request == null || request.token() == null || request.token().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("FCM token is required"))
                .build();
        }

        try {
            UUID userId = UUID.fromString(securityContext.getUserPrincipal().getName());
            boolean isAdmin = securityContext.isUserInRole("admin");
            
            fcmTokenService.registerToken(userId, isAdmin, request.token());

            return Response.ok(FcmTokenResponse.ok()).build();
            
        } catch (IllegalArgumentException e) {
            Log.error("Invalid user ID in security context", e);
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse("Invalid authentication"))
                .build();
        } catch (Exception e) {
            Log.error("Failed to register FCM token", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Failed to register FCM token"))
                .build();
        }
    }

    /**
     * Unregister an FCM token.
     * Typically called when the user logs out or disables notifications.
     */
    @DELETE
    @Path("/{token}")
    public Response unregisterFcmToken(
            @PathParam("token") String token,
            @Context SecurityContext securityContext) {
        
        if (token == null || token.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("FCM token is required"))
                .build();
        }

        try {
            UUID userId = UUID.fromString(securityContext.getUserPrincipal().getName());
            boolean isAdmin = securityContext.isUserInRole("admin");
            
            fcmTokenService.unregisterToken(userId, isAdmin);

            return Response.ok(FcmTokenResponse.ok("FCM token unregistered")).build();
            
        } catch (IllegalArgumentException e) {
            Log.error("Invalid user ID in security context", e);
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse("Invalid authentication"))
                .build();
        } catch (Exception e) {
            Log.error("Failed to unregister FCM token", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Failed to unregister FCM token"))
                .build();
        }
    }
}
