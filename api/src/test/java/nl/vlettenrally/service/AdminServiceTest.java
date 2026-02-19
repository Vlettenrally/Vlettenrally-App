package nl.vlettenrally.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import nl.vlettenrally.data.domain.Admin;
import nl.vlettenrally.data.enums.Role;
import nl.vlettenrally.data.repository.AdminRepository;
import nl.vlettenrally.service.impl.AdminServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

/**
 * Unit tests for AdminService.
 * Tests business logic for admin user management operations.
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    AdminRepository adminRepository;

    @Mock
    AdminRoleValidator roleValidator;

    @InjectMocks
    AdminServiceImpl adminService;

    private UUID adminId;
    private UUID superAdminId;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
        superAdminId = UUID.randomUUID();
    }

    // ==================== getAllAdmins ====================

    @Test
    void getAllAdmins_returnsListFromRepository() {
        // Arrange
        Admin admin1 = createAdmin("admin1", Set.of(Role.ADMIN));
        Admin admin2 = createAdmin("admin2", Set.of(Role.SUPER_ADMIN));
        List<Admin> expected = List.of(admin1, admin2);
        when(adminRepository.findAll()).thenReturn(expected);

        // Act
        List<Admin> result = adminService.getAllAdmins();

        // Assert
        assertEquals(2, result.size());
        assertEquals(expected, result);
        verify(adminRepository).findAll();
    }

    @Test
    void getAllAdmins_returnsEmptyList() {
        // Arrange
        when(adminRepository.findAll()).thenReturn(Collections.emptyList());

        // Act
        List<Admin> result = adminService.getAllAdmins();

        // Assert
        assertTrue(result.isEmpty());
        verify(adminRepository).findAll();
    }

    // ==================== findAdminById ====================

    @Test
    void findAdminById_adminExists_returnsOptionalWithAdmin() {
        // Arrange
        Admin admin = createAdmin("testadmin", Set.of(Role.ADMIN));
        admin.setId(adminId);
        when(adminRepository.findById(adminId)).thenReturn(Optional.of(admin));

        // Act
        Optional<Admin> result = adminService.findAdminById(adminId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(admin, result.get());
        verify(adminRepository).findById(adminId);
    }

    @Test
    void findAdminById_adminNotFound_returnsEmpty() {
        // Arrange
        when(adminRepository.findById(adminId)).thenReturn(Optional.empty());

        // Act
        Optional<Admin> result = adminService.findAdminById(adminId);

        // Assert
        assertTrue(result.isEmpty());
        verify(adminRepository).findById(adminId);
    }

    // ==================== findAdminByUsername ====================

    @Test
    void findAdminByUsername_adminExists_returnsOptionalWithAdmin() {
        // Arrange
        String username = "testadmin";
        Admin admin = createAdmin(username, Set.of(Role.ADMIN));
        when(adminRepository.findByUsername(username)).thenReturn(Optional.of(admin));

        // Act
        Optional<Admin> result = adminService.findAdminByUsername(username);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(admin, result.get());
        verify(adminRepository).findByUsername(username);
    }

    @Test
    void findAdminByUsername_adminNotFound_returnsEmpty() {
        // Arrange
        when(adminRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        // Act
        Optional<Admin> result = adminService.findAdminByUsername("nonexistent");

        // Assert
        assertTrue(result.isEmpty());
    }

    // ==================== createAdmin ====================

    @Test
    void createAdmin_validInput_createsAndReturnsAdmin() {
        // Arrange
        String username = "newadmin";
        String password = "SecurePassword123";
        Set<Role> roles = Set.of(Role.ADMIN);

        when(adminRepository.findByUsername(username)).thenReturn(Optional.empty());
        when(roleValidator.validateRoleCombination(roles)).thenReturn(null);
        when(adminRepository.save(any(Admin.class))).thenAnswer(invocation -> {
            Admin admin = invocation.getArgument(0);
            admin.setId(adminId);
            return admin;
        });

        // Act
        Admin result = adminService.createAdmin(username, password, roles);

        // Assert
        assertNotNull(result);
        assertEquals(username, result.getUsername());
        assertEquals(roles, result.getRoles());
        assertTrue(result.isActive());
        verify(adminRepository).findByUsername(username);
        verify(roleValidator).validateRoleCombination(roles);
        verify(adminRepository).save(any(Admin.class));
    }

    @Test
    void createAdmin_nullUsername_throwsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
            adminService.createAdmin(null, "SecurePassword123", Set.of(Role.ADMIN))
        );
    }

    @Test
    void createAdmin_blankUsername_throwsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
            adminService.createAdmin("", "SecurePassword123", Set.of(Role.ADMIN))
        );
    }

    @Test
    void createAdmin_nullPassword_throwsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
            adminService.createAdmin("username", null, Set.of(Role.ADMIN))
        );
    }

    @Test
    void createAdmin_weakPassword_throwsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
            adminService.createAdmin("username", "weak", Set.of(Role.ADMIN))
        );
    }

    @Test
    void createAdmin_passwordMissingUppercase_throwsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
            adminService.createAdmin("username", "securepassword123", Set.of(Role.ADMIN))
        );
    }

    @Test
    void createAdmin_passwordMissingLowercase_throwsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
            adminService.createAdmin("username", "SECUREPASSWORD123", Set.of(Role.ADMIN))
        );
    }

    @Test
    void createAdmin_passwordMissingNumber_throwsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
            adminService.createAdmin("username", "SecurePassword", Set.of(Role.ADMIN))
        );
    }

    @Test
    void createAdmin_emptyRoles_throwsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
            adminService.createAdmin("username", "SecurePassword123", Collections.emptySet())
        );
    }

    @Test
    void createAdmin_invalidRoleCombination_throwsException() {
        // Arrange
        Set<Role> roles = Set.of(Role.ADMIN);
        when(roleValidator.validateRoleCombination(roles)).thenReturn("Invalid role combination");

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
            adminService.createAdmin("username", "SecurePassword123", roles)
        );
    }

    @Test
    void createAdmin_usernameAlreadyExists_throwsException() {
        // Arrange
        String username = "existinguser";
        Admin existingAdmin = createAdmin(username, Set.of(Role.ADMIN));
        when(adminRepository.findByUsername(username)).thenReturn(Optional.of(existingAdmin));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
            adminService.createAdmin(username, "SecurePassword123", Set.of(Role.ADMIN))
        );
    }

    // ==================== updateAdminRoles ====================

    @Test
    void updateAdminRoles_validRoles_updatesAndReturnsAdmin() {
        // Arrange
        Admin admin = createAdmin("admin", Set.of(Role.ADMIN));
        admin.setId(adminId);
        Set<Role> newRoles = Set.of(Role.SUPER_ADMIN, Role.ADMIN);

        when(adminRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(roleValidator.validateRoleCombination(newRoles)).thenReturn(null);
        when(adminRepository.save(any(Admin.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Admin result = adminService.updateAdminRoles(adminId, newRoles, superAdminId);

        // Assert
        assertEquals(newRoles, result.getRoles());
        verify(adminRepository).save(admin);
    }

    @Test
    void updateAdminRoles_emptyRoles_throwsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
            adminService.updateAdminRoles(adminId, Collections.emptySet(), superAdminId)
        );
    }

    @Test
    void updateAdminRoles_invalidRoleCombination_throwsException() {
        // Arrange
        Set<Role> roles = Set.of(Role.ADMIN);
        when(roleValidator.validateRoleCombination(roles)).thenReturn("Invalid combination");

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
            adminService.updateAdminRoles(adminId, roles, superAdminId)
        );
    }

    @Test
    void updateAdminRoles_adminNotFound_throwsException() {
        // Arrange
        when(adminRepository.findById(adminId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
            adminService.updateAdminRoles(adminId, Set.of(Role.ADMIN), superAdminId)
        );
    }

    @Test
    void updateAdminRoles_selfDemotionFromSuperAdmin_throwsException() {
        // Arrange
        Admin admin = createSuperAdmin("admin", true);
        admin.setId(adminId);
        Set<Role> newRoles = Set.of(Role.ADMIN);

        when(adminRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(roleValidator.validateRoleCombination(newRoles)).thenReturn(null);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
            adminService.updateAdminRoles(adminId, newRoles, adminId) // Same ID = self
        );
    }

    @Test
    void updateAdminRoles_removingLastSuperAdmin_throwsException() {
        // Arrange
        Admin onlySuperAdmin = createSuperAdmin("superadmin", true);
        onlySuperAdmin.setId(adminId);
        Set<Role> newRoles = Set.of(Role.ADMIN);

        when(adminRepository.findById(adminId)).thenReturn(Optional.of(onlySuperAdmin));
        when(roleValidator.validateRoleCombination(newRoles)).thenReturn(null);
        when(adminRepository.findAll()).thenReturn(List.of(onlySuperAdmin));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
            adminService.updateAdminRoles(adminId, newRoles, superAdminId)
        );
    }

    // ==================== resetAdminPassword ====================

    @Test
    void resetAdminPassword_validAdmin_returnsTemporaryPassword() {
        // Arrange
        Admin admin = createAdmin("admin", Set.of(Role.ADMIN));
        admin.setId(adminId);
        when(adminRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(adminRepository.save(any(Admin.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        String tempPassword = adminService.resetAdminPassword(adminId);

        // Assert
        assertNotNull(tempPassword);
        assertTrue(tempPassword.length() >= 8);
        assertTrue(tempPassword.matches(".*[A-Z].*")); // Has uppercase
        assertTrue(tempPassword.matches(".*[a-z].*")); // Has lowercase
        assertTrue(tempPassword.matches(".*[0-9].*")); // Has number
        verify(adminRepository).save(admin);
    }

    @Test
    void resetAdminPassword_adminNotFound_throwsException() {
        // Arrange
        when(adminRepository.findById(adminId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
            adminService.resetAdminPassword(adminId)
        );
    }

    // ==================== deactivateAdmin ====================

    @Test
    void deactivateAdmin_validAdmin_deactivatesAndReturns() {
        // Arrange
        Admin admin = createAdmin("admin", Set.of(Role.ADMIN));
        admin.setId(adminId);
        when(adminRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(adminRepository.save(any(Admin.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Admin result = adminService.deactivateAdmin(adminId, superAdminId);

        // Assert
        assertFalse(result.isActive());
        verify(adminRepository).save(admin);
    }

    @Test
    void deactivateAdmin_selfDeactivation_throwsException() {
        // Arrange
        Admin admin = createAdmin("admin", Set.of(Role.ADMIN));
        admin.setId(adminId);
        when(adminRepository.findById(adminId)).thenReturn(Optional.of(admin));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
            adminService.deactivateAdmin(adminId, adminId) // Same ID = self
        );
    }

    @Test
    void deactivateAdmin_adminNotFound_throwsException() {
        // Arrange
        when(adminRepository.findById(adminId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
            adminService.deactivateAdmin(adminId, superAdminId)
        );
    }

    @Test
    void deactivateAdmin_deactivatingLastSuperAdmin_throwsException() {
        // Arrange
        Admin onlySuperAdmin = createSuperAdmin("superadmin", true);
        onlySuperAdmin.setId(adminId);
        when(adminRepository.findById(adminId)).thenReturn(Optional.of(onlySuperAdmin));
        when(adminRepository.findAll()).thenReturn(List.of(onlySuperAdmin));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
            adminService.deactivateAdmin(adminId, superAdminId)
        );
    }

    // ==================== countActiveSuperAdmins ====================

    @Test
    void countActiveSuperAdmins_multipleSuperAdmins_returnsCorrectCount() {
        // Arrange
        List<Admin> admins = List.of(
            createSuperAdmin("super1", true),
            createSuperAdmin("super2", true),
            createAdmin("admin1", Set.of(Role.ADMIN))
        );
        when(adminRepository.findAll()).thenReturn(admins);

        // Act
        long count = adminService.countActiveSuperAdmins();

        // Assert
        assertEquals(2, count);
    }

    @Test
    void countActiveSuperAdmins_inactiveSuperAdminExcluded() {
        // Arrange
        Admin inactiveSuper = createSuperAdmin("inactive", false);
        List<Admin> admins = List.of(
            createSuperAdmin("active", true),
            inactiveSuper
        );
        when(adminRepository.findAll()).thenReturn(admins);

        // Act
        long count = adminService.countActiveSuperAdmins();

        // Assert
        assertEquals(1, count);
    }

    @Test
    void countActiveSuperAdmins_noSuperAdmins_returnsZero() {
        // Arrange
        List<Admin> admins = List.of(
            createAdmin("admin1", Set.of(Role.ADMIN)),
            createAdmin("admin2", Set.of(Role.VIEWER))
        );
        when(adminRepository.findAll()).thenReturn(admins);

        // Act
        long count = adminService.countActiveSuperAdmins();

        // Assert
        assertEquals(0, count);
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

    private Admin createSuperAdmin(String username, boolean active) {
        Admin admin = createAdmin(username, Set.of(Role.SUPER_ADMIN));
        admin.setActive(active);
        return admin;
    }
}

