package nl.vlettenrally.api.v1;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import nl.vlettenrally.data.domain.Admin;
import nl.vlettenrally.data.dto.admin.AdminReferencesResponse;
import nl.vlettenrally.data.dto.admin.AdminUserResponse;
import nl.vlettenrally.data.dto.admin.CreateAdminRequest;
import nl.vlettenrally.data.dto.admin.ResetPasswordResponse;
import nl.vlettenrally.data.dto.admin.UpdateAdminRolesRequest;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.data.enums.Role;
import nl.vlettenrally.service.AdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.*;

/**
 * Unit tests for AdminResource.
 * Tests HTTP endpoints for admin user management.
 */
@ExtendWith(MockitoExtension.class)
class AdminResourceTest {

    @Mock
    AdminService adminService;

    @Mock
    SecurityContext securityContext;

    @Mock
    Principal principal;

    @InjectMocks
    AdminResource adminResource;

    private UUID adminId;
    private UUID currentAdminId;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
        currentAdminId = UUID.randomUUID();
    }

    // Helper method for tests that need security context
    private void setupSecurityContext() {
        when(securityContext.getUserPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn(currentAdminId.toString());
    }

    // ==================== listAdmins ====================

    @Test
    void listAdmins_success_returnsOkWithAdminsList() {
        // Arrange
        Admin admin1 = createAdmin("admin1", Set.of(Role.ADMIN));
        Admin admin2 = createAdmin("admin2", Set.of(Role.SUPER_ADMIN));
        when(adminService.getAllAdmins()).thenReturn(List.of(admin1, admin2));

        // Act & Assert
        try (Response response = adminResource.listAdmins(securityContext)) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            assertNotNull(response.getEntity());
            assertInstanceOf(List.class, response.getEntity());
            verify(adminService).getAllAdmins();
        }
    }

    @Test
    void listAdmins_emptyList_returnsOkWithEmptyList() {
        // Arrange
        when(adminService.getAllAdmins()).thenReturn(Collections.emptyList());

        // Act & Assert
        try (Response response = adminResource.listAdmins(securityContext)) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            List<?> admins = (List<?>) response.getEntity();
            assertTrue(admins.isEmpty());
        }
    }

    // ==================== createAdmin ====================

    @Test
    void createAdmin_validInput_returnsCreatedWithAdmin() {
        // Arrange
        CreateAdminRequest request = new CreateAdminRequest("newadmin", "SecurePassword123", Set.of(Role.ADMIN));
        Admin created = createAdmin("newadmin", Set.of(Role.ADMIN));
        when(adminService.createAdmin("newadmin", "SecurePassword123", Set.of(Role.ADMIN))).thenReturn(created);

        // Act & Assert
        try (Response response = adminResource.createAdmin(request, securityContext)) {
            assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
            AdminUserResponse entity = (AdminUserResponse) response.getEntity();
            assertEquals("newadmin", entity.username());
            verify(adminService).createAdmin("newadmin", "SecurePassword123", Set.of(Role.ADMIN));
        }
    }

    @Test
    void createAdmin_usernameAlreadyExists_returnsConflict() {
        // Arrange
        CreateAdminRequest request = new CreateAdminRequest("existing", "SecurePassword123", Set.of(Role.ADMIN));
        when(adminService.createAdmin(anyString(), anyString(), anySet()))
            .thenThrow(new IllegalArgumentException("Username already exists"));

        // Act & Assert
        try (Response response = adminResource.createAdmin(request, securityContext)) {
            assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());
            ErrorResponse error = (ErrorResponse) response.getEntity();
            assertTrue(error.message().contains("already exists"));
        }
    }

    @Test
    void createAdmin_invalidPassword_returnsBadRequest() {
        // Arrange
        CreateAdminRequest request = new CreateAdminRequest("admin", "weak", Set.of(Role.ADMIN));
        when(adminService.createAdmin(anyString(), anyString(), anySet()))
            .thenThrow(new IllegalArgumentException("Password must be at least 8 characters long"));

        // Act & Assert
        try (Response response = adminResource.createAdmin(request, securityContext)) {
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
            ErrorResponse error = (ErrorResponse) response.getEntity();
            assertTrue(error.message().contains("Password"));
        }
    }

    @Test
    void createAdmin_invalidRoles_returnsBadRequest() {
        // Arrange
        CreateAdminRequest request = new CreateAdminRequest("admin", "SecurePassword123", Set.of(Role.ADMIN));
        when(adminService.createAdmin(anyString(), anyString(), anySet()))
            .thenThrow(new IllegalArgumentException("VIEWER role cannot be combined with other roles"));

        // Act & Assert
        try (Response response = adminResource.createAdmin(request, securityContext)) {
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        }
    }

    // ==================== updateRoles ====================

    @Test
    void updateRoles_validRoles_returnsOkWithUpdatedAdmin() {
        // Arrange
        setupSecurityContext();
        UpdateAdminRolesRequest request = new UpdateAdminRolesRequest(Set.of(Role.SUPER_ADMIN, Role.ADMIN));
        Admin updated = createAdmin("admin", Set.of(Role.SUPER_ADMIN, Role.ADMIN));
        updated.setId(adminId);
        when(adminService.updateAdminRoles(adminId, request.roles(), currentAdminId)).thenReturn(updated);

        // Act & Assert
        try (Response response = adminResource.updateRoles(adminId, request, securityContext)) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            AdminUserResponse entity = (AdminUserResponse) response.getEntity();
            assertTrue(entity.roles().contains(Role.SUPER_ADMIN));
            verify(adminService).updateAdminRoles(adminId, request.roles(), currentAdminId);
        }
    }

    @Test
    void updateRoles_adminNotFound_returnsNotFound() {
        // Arrange
        setupSecurityContext();
        UpdateAdminRolesRequest request = new UpdateAdminRolesRequest(Set.of(Role.ADMIN));
        when(adminService.updateAdminRoles(adminId, request.roles(), currentAdminId))
            .thenThrow(new IllegalArgumentException("Admin user not found"));

        // Act & Assert
        try (Response response = adminResource.updateRoles(adminId, request, securityContext)) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
            ErrorResponse error = (ErrorResponse) response.getEntity();
            assertTrue(error.message().contains("not found"));
        }
    }

    @Test
    void updateRoles_selfDemotionFromSuperAdmin_returnsForbidden() {
        // Arrange
        setupSecurityContext();
        UpdateAdminRolesRequest request = new UpdateAdminRolesRequest(Set.of(Role.ADMIN));
        when(adminService.updateAdminRoles(adminId, request.roles(), currentAdminId))
            .thenThrow(new IllegalArgumentException("Cannot remove SUPER_ADMIN role from yourself"));

        // Act & Assert
        try (Response response = adminResource.updateRoles(adminId, request, securityContext)) {
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
            ErrorResponse error = (ErrorResponse) response.getEntity();
            assertTrue(error.message().contains("SUPER_ADMIN"));
        }
    }

    @Test
    void updateRoles_removingLastSuperAdmin_returnsForbidden() {
        // Arrange
        setupSecurityContext();
        UpdateAdminRolesRequest request = new UpdateAdminRolesRequest(Set.of(Role.ADMIN));
        when(adminService.updateAdminRoles(adminId, request.roles(), currentAdminId))
            .thenThrow(new IllegalArgumentException("Cannot remove SUPER_ADMIN role from the last super admin"));

        // Act & Assert
        try (Response response = adminResource.updateRoles(adminId, request, securityContext)) {
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
            ErrorResponse error = (ErrorResponse) response.getEntity();
            assertTrue(error.message().contains("last super admin"));
        }
    }

    @Test
    void updateRoles_invalidRoleCombination_returnsBadRequest() {
        // Arrange
        setupSecurityContext();
        UpdateAdminRolesRequest request = new UpdateAdminRolesRequest(Set.of(Role.VIEWER, Role.ADMIN));
        when(adminService.updateAdminRoles(adminId, request.roles(), currentAdminId))
            .thenThrow(new IllegalArgumentException("VIEWER role cannot be combined with other roles"));

        // Act & Assert
        try (Response response = adminResource.updateRoles(adminId, request, securityContext)) {
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        }
    }

    // ==================== resetPassword ====================

    @Test
    void resetPassword_validAdmin_returnsOkWithTemporaryPassword() {
        // Arrange
        String tempPassword = "TempPass123!";
        when(adminService.resetAdminPassword(adminId)).thenReturn(tempPassword);

        // Act & Assert
        try (Response response = adminResource.resetPassword(adminId, securityContext)) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            ResetPasswordResponse entity = (ResetPasswordResponse) response.getEntity();
            assertEquals(tempPassword, entity.temporaryPassword());
            verify(adminService).resetAdminPassword(adminId);
        }
    }

    @Test
    void resetPassword_adminNotFound_returnsNotFound() {
        // Arrange
        when(adminService.resetAdminPassword(adminId))
            .thenThrow(new IllegalArgumentException("Admin user not found"));

        // Act & Assert
        try (Response response = adminResource.resetPassword(adminId, securityContext)) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
            ErrorResponse error = (ErrorResponse) response.getEntity();
            assertTrue(error.message().contains("not found"));
        }
    }

    // ==================== deactivateAdmin ====================

    @Test
    void deactivateAdmin_validAdmin_returnsOkWithUpdatedAdmin() {
        // Arrange
        setupSecurityContext();
        Admin deactivated = createAdmin("admin", Set.of(Role.ADMIN));
        deactivated.setActive(false);
        when(adminService.deactivateAdmin(adminId, currentAdminId)).thenReturn(deactivated);

        // Act & Assert
        try (Response response = adminResource.deactivateAdmin(adminId, securityContext)) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            AdminUserResponse body = (AdminUserResponse) response.getEntity();
            assertFalse(body.active());
            verify(adminService).deactivateAdmin(adminId, currentAdminId);
        }
    }

    @Test
    void deactivateAdmin_adminNotFound_returnsNotFound() {
        // Arrange
        setupSecurityContext();
        when(adminService.deactivateAdmin(adminId, currentAdminId))
            .thenThrow(new IllegalArgumentException("Admin user not found"));

        // Act & Assert
        try (Response response = adminResource.deactivateAdmin(adminId, securityContext)) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
            ErrorResponse error = (ErrorResponse) response.getEntity();
            assertTrue(error.message().contains("not found"));
        }
    }

    @Test
    void deactivateAdmin_selfDeactivation_returnsForbidden() {
        // Arrange
        setupSecurityContext();
        when(adminService.deactivateAdmin(adminId, currentAdminId))
            .thenThrow(new IllegalArgumentException("Cannot deactivate your own account"));

        // Act & Assert
        try (Response response = adminResource.deactivateAdmin(adminId, securityContext)) {
            assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());
            ErrorResponse error = (ErrorResponse) response.getEntity();
            assertTrue(error.message().contains("own account"));
        }
    }

    @Test
    void deactivateAdmin_deactivatingLastSuperAdmin_returnsForbidden() {
        // Arrange
        setupSecurityContext();
        when(adminService.deactivateAdmin(adminId, currentAdminId))
            .thenThrow(new IllegalArgumentException("Cannot deactivate the last super admin"));

        // Act & Assert
        try (Response response = adminResource.deactivateAdmin(adminId, securityContext)) {
            assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());
            ErrorResponse error = (ErrorResponse) response.getEntity();
            assertTrue(error.message().contains("last super admin"));
        }
    }

    // ==================== activateAdmin ====================

    @Test
    void activateAdmin_validAdmin_returnsOkWithUpdatedAdmin() {
        // Arrange
        setupSecurityContext();
        when(adminService.activateAdmin(adminId)).thenReturn(createAdmin("admin", Set.of(Role.ADMIN)));

        // Act & Assert
        try (Response response = adminResource.activateAdmin(adminId, securityContext)) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            AdminUserResponse body = (AdminUserResponse) response.getEntity();
            assertTrue(body.active());
            verify(adminService).activateAdmin(adminId);
        }
    }

    @Test
    void activateAdmin_adminNotFound_returnsNotFound() {
        // Arrange
        when(adminService.activateAdmin(adminId))
            .thenThrow(new IllegalArgumentException("Admin user not found"));

        // Act & Assert
        try (Response response = adminResource.activateAdmin(adminId, securityContext)) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
            ErrorResponse error = (ErrorResponse) response.getEntity();
            assertTrue(error.message().contains("not found"));
        }
    }

    // ==================== getAdminReferences ====================

    @Test
    void getAdminReferences_validAdmin_returnsOkWithCounts() {
        // Arrange
        AdminReferencesResponse references = new AdminReferencesResponse(1L, 2L, 3L, 4L, 5L);
        when(adminService.countAdminReferences(adminId)).thenReturn(references);

        // Act & Assert
        try (Response response = adminResource.getAdminReferences(adminId)) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            assertEquals(references, response.getEntity());
        }
    }

    @Test
    void getAdminReferences_adminNotFound_returnsNotFound() {
        // Arrange
        when(adminService.countAdminReferences(adminId))
            .thenThrow(new IllegalArgumentException("Admin user not found"));

        // Act & Assert
        try (Response response = adminResource.getAdminReferences(adminId)) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
        }
    }

    // ==================== deleteAdmin ====================

    @Test
    void deleteAdmin_validAdmin_returnsNoContent() {
        // Arrange
        setupSecurityContext();

        // Act & Assert
        try (Response response = adminResource.deleteAdmin(adminId, securityContext)) {
            assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
            verify(adminService).deleteAdmin(adminId, currentAdminId);
        }
    }

    @Test
    void deleteAdmin_adminNotFound_returnsNotFound() {
        // Arrange
        setupSecurityContext();
        doThrow(new IllegalArgumentException("Admin user not found"))
            .when(adminService).deleteAdmin(adminId, currentAdminId);

        // Act & Assert
        try (Response response = adminResource.deleteAdmin(adminId, securityContext)) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
            ErrorResponse error = (ErrorResponse) response.getEntity();
            assertTrue(error.message().contains("not found"));
        }
    }

    @Test
    void deleteAdmin_selfDeletion_returnsForbidden() {
        // Arrange
        setupSecurityContext();
        doThrow(new IllegalArgumentException("Cannot delete your own account"))
            .when(adminService).deleteAdmin(adminId, currentAdminId);

        // Act & Assert
        try (Response response = adminResource.deleteAdmin(adminId, securityContext)) {
            assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());
            ErrorResponse error = (ErrorResponse) response.getEntity();
            assertTrue(error.message().contains("own account"));
        }
    }

    @Test
    void deleteAdmin_stillActive_returnsConflict() {
        // Arrange
        setupSecurityContext();
        doThrow(new IllegalStateException("Deactivate the admin user before deleting it"))
            .when(adminService).deleteAdmin(adminId, currentAdminId);

        // Act & Assert
        try (Response response = adminResource.deleteAdmin(adminId, securityContext)) {
            assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());
            ErrorResponse error = (ErrorResponse) response.getEntity();
            assertTrue(error.message().contains("Deactivate"));
        }
    }

    @Test
    void deleteAdmin_withAttributedContent_returnsConflict() {
        // Arrange
        setupSecurityContext();
        doThrow(new IllegalStateException("Cannot delete an admin user with attributed content: 3 sent messages"))
            .when(adminService).deleteAdmin(adminId, currentAdminId);

        // Act & Assert
        try (Response response = adminResource.deleteAdmin(adminId, securityContext)) {
            assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());
            ErrorResponse error = (ErrorResponse) response.getEntity();
            assertTrue(error.message().contains("3 sent messages"));
        }
    }

    // ==================== Helper Methods ====================

    private Admin createAdmin(String username, Set<Role> roles) {
        Admin admin = new Admin();
        admin.setId(UUID.randomUUID());
        admin.setUsername(username);
        admin.setPasswordHash("hashed");
        admin.setRoles(roles);
        admin.setActive(true);
        return admin;
    }
}






















