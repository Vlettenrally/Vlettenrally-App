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
import nl.vlettenrally.data.domain.DeviceRegistration;
import nl.vlettenrally.data.domain.FcmToken;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.data.dto.device.FcmTokenRequest;
import nl.vlettenrally.data.dto.device.FcmTokenResponse;
import nl.vlettenrally.data.repository.DeviceRegistrationRepository;
import nl.vlettenrally.data.repository.FcmTokenRepository;

import java.util.Optional;
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
    FcmTokenRepository fcmTokenRepository;

    @Inject
    DeviceRegistrationRepository deviceRegistrationRepository;

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
            
            // Find or create device registration for this user (team or admin)
            DeviceRegistration deviceRegistration;
            if (isAdmin) {
                deviceRegistration = deviceRegistrationRepository.findByAdminId(userId)
                    .orElseGet(() -> {
                        DeviceRegistration newRegistration = new DeviceRegistration();
                        newRegistration.setAdminId(userId);
                        return deviceRegistrationRepository.save(newRegistration);
                    });
            } else {
                deviceRegistration = deviceRegistrationRepository.findByTeamId(userId)
                    .orElseGet(() -> {
                        DeviceRegistration newRegistration = new DeviceRegistration();
                        newRegistration.setTeamId(userId);
                        return deviceRegistrationRepository.save(newRegistration);
                    });
            }
            
            // Update last seen
            deviceRegistration.touch();
            deviceRegistrationRepository.save(deviceRegistration);

            // Find existing FCM token for this device or create new one
            Optional<FcmToken> existingToken = fcmTokenRepository.findByDeviceRegistrationId(deviceRegistration.getId());
            
            String userType = isAdmin ? "admin" : "team";
            if (existingToken.isPresent()) {
                // Update existing token
                FcmToken fcmToken = existingToken.get();
                fcmToken.setToken(request.token());
                fcmTokenRepository.save(fcmToken);
                Log.infof("Updated FCM token for %s %s", userType, userId);
            } else {
                // Create new token
                FcmToken fcmToken = new FcmToken();
                fcmToken.setDeviceRegistrationId(deviceRegistration.getId());
                fcmToken.setToken(request.token());
                fcmTokenRepository.save(fcmToken);
                Log.infof("Registered new FCM token for %s %s", userType, userId);
            }

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
            
            // Find device registration for this user (team or admin)
            Optional<DeviceRegistration> deviceRegistration = isAdmin 
                ? deviceRegistrationRepository.findByAdminId(userId)
                : deviceRegistrationRepository.findByTeamId(userId);
            
            if (deviceRegistration.isPresent()) {
                // Delete FCM token for this device
                fcmTokenRepository.deleteByDeviceRegistrationId(deviceRegistration.get().getId());
                String userType = isAdmin ? "admin" : "team";
                Log.infof("Unregistered FCM token for %s %s", userType, userId);
            }

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
