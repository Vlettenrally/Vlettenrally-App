package nl.vlettenrally.api.v1;

import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import nl.vlettenrally.api.security.SecurityRoles;
import nl.vlettenrally.data.domain.DeviceRegistration;
import nl.vlettenrally.data.dto.auth.DeviceResponse;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.data.dto.device.DeviceRenameRequest;
import nl.vlettenrally.data.repository.DeviceRegistrationRepository;
import nl.vlettenrally.data.repository.FcmTokenRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Self-service device management for the authenticated admin.
 * <p>
 * Endpoints (require any admin/staff role):
 * - GET    /api/v1/admin/me/devices           - list the admin's registered devices
 * - PATCH  /api/v1/admin/me/devices/{id}      - rename a device
 * - DELETE /api/v1/admin/me/devices/{id}      - remove a device (also drops its FCM token)
 * <p>
 * Each row in {@code device_registrations} owned by the admin represents one browser
 * profile that has registered for push notifications. Removing a device here is the
 * admin equivalent of revoking push for that browser; subsequent FCM messages won't
 * reach it.
 */
@Path("/api/v1/admin/me/devices")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed(SecurityRoles.STAFF)
public class AdminDeviceResource {

    @Inject
    DeviceRegistrationRepository deviceRegistrationRepository;

    @Inject
    FcmTokenRepository fcmTokenRepository;

    @GET
    public Response listMyDevices(@Context SecurityContext securityContext) {
        UUID adminId = UUID.fromString(securityContext.getUserPrincipal().getName());
        List<DeviceResponse> devices = deviceRegistrationRepository.findAllByAdminId(adminId).stream()
            .map(d -> new DeviceResponse(
                d.getId(),
                d.getNickname(),
                false, // admins don't have a "main" device concept
                d.getCreatedAt(),
                d.getLastSeen()
            ))
            .toList();
        return Response.ok(devices).build();
    }

    @PATCH
    @Path("/{deviceId}")
    public Response renameMyDevice(@PathParam("deviceId") UUID deviceId,
                                   DeviceRenameRequest request,
                                   @Context SecurityContext securityContext) {
        if (request == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("Body is required"))
                .build();
        }
        UUID adminId = UUID.fromString(securityContext.getUserPrincipal().getName());

        Optional<DeviceRegistration> deviceOpt = deviceRegistrationRepository.findByAdminIdAndId(adminId, deviceId);
        if (deviceOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Device not found"))
                .build();
        }

        DeviceRegistration device = deviceOpt.get();
        String trimmed = request.nickname() == null ? null : request.nickname().trim();
        device.setNickname(trimmed == null || trimmed.isEmpty() ? null : trimmed);
        DeviceRegistration saved = deviceRegistrationRepository.save(device);

        return Response.ok(new DeviceResponse(
            saved.getId(),
            saved.getNickname(),
            false,
            saved.getCreatedAt(),
            saved.getLastSeen()
        )).build();
    }

    @DELETE
    @Path("/{deviceId}")
    public Response removeMyDevice(@PathParam("deviceId") UUID deviceId,
                                   @Context SecurityContext securityContext) {
        UUID adminId = UUID.fromString(securityContext.getUserPrincipal().getName());

        Optional<DeviceRegistration> deviceOpt = deviceRegistrationRepository.findByAdminIdAndId(adminId, deviceId);
        if (deviceOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Device not found"))
                .build();
        }

        // Drop the FCM token row first so the device stops receiving pushes
        // immediately, then delete the device registration itself.
        fcmTokenRepository.deleteByDeviceRegistrationId(deviceId);
        deviceRegistrationRepository.delete(deviceId);

        Log.infof("Admin device removed - adminId: %s, deviceId: %s", adminId, deviceId);
        return Response.noContent().build();
    }
}
