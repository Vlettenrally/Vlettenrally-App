package nl.vlettenrally.api.v1;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import nl.vlettenrally.data.domain.Admin;
import nl.vlettenrally.data.domain.AdminRole;
import nl.vlettenrally.data.dto.admin.AdminUserResponse;
import nl.vlettenrally.data.dto.admin.CreateAdminRequest;
import nl.vlettenrally.data.dto.admin.ResetPasswordResponse;
import nl.vlettenrally.data.dto.admin.UpdateAdminRolesRequest;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.data.repository.AdminRepository;
import nl.vlettenrally.service.AdminRoleValidator;

import java.security.SecureRandom;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST Resource for Admin User Management
 * Only accessible by users with SUPER_ADMIN role
 * 
 * Endpoints:
 * - GET /api/v1/admins - List all admin users
 * - POST /api/v1/admins - Create a new admin user
 * - PUT /api/v1/admins/{id}/roles - Update admin roles
 * - PUT /api/v1/admins/{id}/reset-password - Reset admin password
 * - DELETE /api/v1/admins/{id} - Deactivate admin user
 * 
 * TODO: Add @RolesAllowed("CHECKPOINT_MANAGER") to achievement verification endpoints
 */
@Path("/api/v1/admins")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("SUPER_ADMIN")
public class AdminResource {

    @Inject
    AdminRepository adminRepository;

    @Inject
    AdminRoleValidator roleValidator;

    private static final String PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%&*";
    private static final int TEMP_PASSWORD_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * List all admin users
     */
    @GET
    @RolesAllowed("SUPER_ADMIN")
    public Response listAdmins(@Context SecurityContext securityContext) {
        List<AdminUserResponse> admins = adminRepository.findAll().stream()
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
    @RolesAllowed("SUPER_ADMIN")
    public Response createAdmin(CreateAdminRequest request, @Context SecurityContext securityContext) {
        // Validate request
        if (request.username() == null || request.username().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("Username is required"))
                .build();
        }
        if (request.password() == null || request.password().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("Password is required"))
                .build();
        }
        if (request.roles() == null || request.roles().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("At least one role is required"))
                .build();
        }

        // Validate password strength
        String passwordError = validatePassword(request.password());
        if (passwordError != null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(passwordError))
                .build();
        }

        // Validate role combination
        String roleError = roleValidator.validateRoleCombination(request.roles());
        if (roleError != null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(roleError))
                .build();
        }

        // Check if username already exists
        if (adminRepository.findByUsername(request.username()).isPresent()) {
            return Response.status(Response.Status.CONFLICT)
                .entity(new ErrorResponse("Username already exists"))
                .build();
        }

        // Create admin
        Admin admin = new Admin();
        admin.setUsername(request.username());
        admin.setPasswordHash(BcryptUtil.bcryptHash(request.password()));
        admin.setRoles(request.roles());
        admin.setActive(true);
        
        Admin savedAdmin = adminRepository.save(admin);
        
        UUID creatorId = getAdminId(securityContext);
        Log.warnf("AUDIT: Admin %s created new admin user %s (username: %s, roles: %s)", 
            creatorId, savedAdmin.getId(), savedAdmin.getUsername(), savedAdmin.getRoles());
        
        return Response.status(Response.Status.CREATED)
            .entity(toResponse(savedAdmin))
            .build();
    }

    /**
     * Update admin roles
     */
    @PUT
    @Path("/{id}/roles")
    @RolesAllowed("SUPER_ADMIN")
    public Response updateRoles(
            @PathParam("id") UUID adminId,
            UpdateAdminRolesRequest request,
            @Context SecurityContext securityContext) {
        
        if (request.roles() == null || request.roles().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("At least one role is required"))
                .build();
        }

        // Validate role combination
        String roleError = roleValidator.validateRoleCombination(request.roles());
        if (roleError != null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(roleError))
                .build();
        }

        Admin admin = adminRepository.findById(adminId).orElse(null);
        if (admin == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Admin user not found"))
                .build();
        }

        UUID currentUserId = getAdminId(securityContext);
        
        // Prevent self-demotion from SUPER_ADMIN
        if (currentUserId.equals(adminId)) {
            if (admin.getRoles().contains(AdminRole.SUPER_ADMIN) && 
                !request.roles().contains(AdminRole.SUPER_ADMIN)) {
                Log.warnf("AUDIT: Admin %s attempted to demote themselves from SUPER_ADMIN", currentUserId);
                return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse("Cannot remove SUPER_ADMIN role from yourself"))
                    .build();
            }
        }

        // Check if this would remove the last SUPER_ADMIN
        if (admin.getRoles().contains(AdminRole.SUPER_ADMIN) && 
            !request.roles().contains(AdminRole.SUPER_ADMIN)) {
            long superAdminCount = adminRepository.findAll().stream()
                .filter(Admin::isActive)
                .filter(a -> a.getRoles().contains(AdminRole.SUPER_ADMIN))
                .count();
            
            if (superAdminCount <= 1) {
                Log.warnf("AUDIT: Admin %s attempted to remove last SUPER_ADMIN role from admin %s", 
                    currentUserId, adminId);
                return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse("Cannot remove SUPER_ADMIN role from the last super admin"))
                    .build();
            }
        }

        Set<AdminRole> oldRoles = admin.getRoles();
        admin.setRoles(request.roles());
        Admin updated = adminRepository.save(admin);
        
        Log.warnf("AUDIT: Admin %s updated roles for admin %s (username: %s) from %s to %s", 
            currentUserId, adminId, admin.getUsername(), oldRoles, request.roles());
        
        return Response.ok(toResponse(updated)).build();
    }

    /**
     * Reset admin password
     */
    @PUT
    @Path("/{id}/reset-password")
    @RolesAllowed("SUPER_ADMIN")
    public Response resetPassword(@PathParam("id") UUID adminId, @Context SecurityContext securityContext) {
        Admin admin = adminRepository.findById(adminId).orElse(null);
        if (admin == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Admin user not found"))
                .build();
        }

        // Generate temporary password
        String tempPassword = generateTemporaryPassword();
        admin.setPasswordHash(BcryptUtil.bcryptHash(tempPassword));
        adminRepository.save(admin);
        
        UUID resetById = getAdminId(securityContext);
        Log.warnf("AUDIT: Admin %s reset password for admin %s (username: %s)", 
            resetById, adminId, admin.getUsername());
        
        return Response.ok(new ResetPasswordResponse(tempPassword)).build();
    }

    /**
     * Deactivate admin user
     */
    @DELETE
    @Path("/{id}")
    @RolesAllowed("SUPER_ADMIN")
    public Response deactivateAdmin(@PathParam("id") UUID adminId, @Context SecurityContext securityContext) {
        Admin admin = adminRepository.findById(adminId).orElse(null);
        if (admin == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Admin user not found"))
                .build();
        }

        UUID currentUserId = getAdminId(securityContext);
        
        // Prevent self-deactivation
        if (currentUserId.equals(adminId)) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ErrorResponse("Cannot deactivate your own account"))
                .build();
        }

        // Check if this would deactivate the last SUPER_ADMIN
        if (admin.getRoles().contains(AdminRole.SUPER_ADMIN)) {
            long superAdminCount = adminRepository.findAll().stream()
                .filter(Admin::isActive)
                .filter(a -> a.getRoles().contains(AdminRole.SUPER_ADMIN))
                .count();
            
            if (superAdminCount <= 1) {
                Log.warnf("AUDIT: Admin %s attempted to deactivate last SUPER_ADMIN admin %s", 
                    currentUserId, adminId);
                return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse("Cannot deactivate the last super admin"))
                    .build();
            }
        }

        admin.setActive(false);
        adminRepository.save(admin);
        
        Log.warnf("AUDIT: Admin %s deactivated admin %s (username: %s)", 
            currentUserId, adminId, admin.getUsername());
        
        return Response.noContent().build();
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

    private String validatePassword(String password) {
        if (password.length() < 8) {
            return "Password must be at least 8 characters long";
        }
        if (!password.matches(".*[A-Z].*")) {
            return "Password must contain at least one uppercase letter";
        }
        if (!password.matches(".*[a-z].*")) {
            return "Password must contain at least one lowercase letter";
        }
        if (!password.matches(".*[0-9].*")) {
            return "Password must contain at least one number";
        }
        return null;
    }

    private String generateTemporaryPassword() {
        StringBuilder password = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            password.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        
        // Ensure it meets password requirements
        String temp = password.toString();
        if (validatePassword(temp) != null) {
            // Try again if it doesn't meet requirements (very unlikely)
            return generateTemporaryPassword();
        }
        
        return temp;
    }
}
