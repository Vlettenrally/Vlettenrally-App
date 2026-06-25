package nl.vlettenrally.api.v1;

import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import nl.vlettenrally.api.security.SecurityRoles;
import nl.vlettenrally.data.domain.Admin;
import nl.vlettenrally.data.dto.admin.AdminUserResponse;
import nl.vlettenrally.data.dto.admin.CreateAdminRequest;
import nl.vlettenrally.data.dto.admin.ResetPasswordResponse;
import nl.vlettenrally.data.dto.admin.UpdateAdminRolesRequest;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.service.AdminService;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST Resource for Admin User Management
 * Only accessible by users with SUPER_ADMIN role
 * <p>
 * Endpoints:
 * - GET /api/v1/admins - List all admin users
 * - POST /api/v1/admins - Create a new admin user
 * - PUT /api/v1/admins/{id}/roles - Update admin roles
 * - PUT /api/v1/admins/{id}/reset-password - Reset admin password
 * - DELETE /api/v1/admins/{id} - Deactivate admin user
 * <p>
 */
@Path("/api/v1/admins")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed(SecurityRoles.SUPER_ADMIN)
public class AdminResource {

    @Inject
    AdminService adminService;

    /**
     * List all admin users
     */
    @GET
    @RolesAllowed(SecurityRoles.SUPER_ADMIN)
    public Response listAdmins(@Context SecurityContext securityContext) {
        List<AdminUserResponse> admins = adminService.getAllAdmins().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());

        UUID adminId = getAdminId(securityContext);
        Log.warnf("AUDIT: Admin %s listed all admin users", adminId);

        return Response.ok(admins).build();
    }

    /**
     * Create a new admin user
     */
    @POST
    @RolesAllowed(SecurityRoles.SUPER_ADMIN)
    public Response createAdmin(CreateAdminRequest request, @Context SecurityContext securityContext) {
        try {
            Admin savedAdmin = adminService.createAdmin(
                request.username(),
                request.password(),
                request.roles()
            );

            UUID creatorId = getAdminId(securityContext);
            Log.warnf("AUDIT: Admin %s created new admin user %s (username: %s, roles: %s)",
                creatorId, savedAdmin.getId(), savedAdmin.getUsername(), savedAdmin.getRoles());

            return Response.status(Response.Status.CREATED)
                .entity(toResponse(savedAdmin))
                .build();
        } catch (IllegalArgumentException e) {
            // Check the error message to determine appropriate HTTP status
            String message = e.getMessage();
            if (message.contains("already exists")) {
                return Response.status(Response.Status.CONFLICT)
                    .entity(new ErrorResponse(message))
                    .build();
            }
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(message))
                .build();
        }
    }

    /**
     * Update admin roles
     */
    @PUT
    @Path("/{id}/roles")
    @RolesAllowed(SecurityRoles.SUPER_ADMIN)
    public Response updateRoles(
            @PathParam("id") UUID adminId,
            UpdateAdminRolesRequest request,
            @Context SecurityContext securityContext) {

        try {
            UUID currentUserId = getAdminId(securityContext);
            Admin updated = adminService.updateAdminRoles(adminId, request.roles(), currentUserId);

            Log.warnf("AUDIT: Admin %s updated roles for admin %s (username: %s) to %s",
                currentUserId, adminId, updated.getUsername(), request.roles());

            return Response.ok(toResponse(updated)).build();
        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            if (message.contains("not found")) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(message))
                    .build();
            }
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(message))
                .build();
        }
    }

    /**
     * Reset admin password
     */
    @PUT
    @Path("/{id}/reset-password")
    @RolesAllowed(SecurityRoles.SUPER_ADMIN)
    public Response resetPassword(@PathParam("id") UUID adminId, @Context SecurityContext securityContext) {
        try {
            String tempPassword = adminService.resetAdminPassword(adminId);

            UUID resetById = getAdminId(securityContext);
            Log.warnf("AUDIT: Admin %s reset password for admin %s", resetById, adminId);

            return Response.ok(new ResetPasswordResponse(tempPassword)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        }
    }

    /**
     * Deactivate admin user
     */
    @DELETE
    @Path("/{id}")
    @RolesAllowed(SecurityRoles.SUPER_ADMIN)
    public Response deactivateAdmin(@PathParam("id") UUID adminId, @Context SecurityContext securityContext) {
        try {
            UUID currentUserId = getAdminId(securityContext);
            adminService.deactivateAdmin(adminId, currentUserId);

            Log.warnf("AUDIT: Admin %s deactivated admin %s", currentUserId, adminId);

            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            if (message.contains("not found")) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(message))
                    .build();
            }
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ErrorResponse(message))
                .build();
        }
    }


    // Helper methods

    private AdminUserResponse toResponse(Admin admin) {
        return new AdminUserResponse(
            admin.getId(),
            admin.getUsername(),
            admin.getRoles(),
            admin.isActive(),
            admin.getCreatedAt(),
            admin.getUpdatedAt()
        );
    }

    private UUID getAdminId(SecurityContext securityContext) {
        if (securityContext.getUserPrincipal() != null) {
            return UUID.fromString(securityContext.getUserPrincipal().getName());
        }
        return null;
    }
}
