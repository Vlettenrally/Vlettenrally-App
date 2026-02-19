package nl.vlettenrally.service.impl;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.Admin;
import nl.vlettenrally.data.enums.Role;
import nl.vlettenrally.data.repository.AdminRepository;
import nl.vlettenrally.service.AdminRoleValidator;
import nl.vlettenrally.service.AdminService;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Implementation of AdminService.
 * Handles business logic for admin user management operations.
 */
@ApplicationScoped
public class AdminServiceImpl implements AdminService {

    @Inject
    AdminRepository adminRepository;

    @Inject
    AdminRoleValidator roleValidator;

    private static final String PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%&*";
    private static final int TEMP_PASSWORD_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public List<Admin> getAllAdmins() {
        return adminRepository.findAll();
    }

    @Override
    public Optional<Admin> findAdminById(UUID id) {
        return adminRepository.findById(id);
    }

    @Override
    public Optional<Admin> findAdminByUsername(String username) {
        return adminRepository.findByUsername(username);
    }

    @Override
    public Admin createAdmin(String username, String password, Set<Role> roles) {
        // Validate inputs
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("At least one role is required");
        }

        // Validate password strength
        String passwordError = validatePassword(password);
        if (passwordError != null) {
            throw new IllegalArgumentException(passwordError);
        }

        // Validate role combination
        String roleError = roleValidator.validateRoleCombination(roles);
        if (roleError != null) {
            throw new IllegalArgumentException(roleError);
        }

        // Check if username already exists
        if (adminRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }

        // Create admin
        Admin admin = new Admin();
        admin.setUsername(username);
        admin.setPasswordHash(BcryptUtil.bcryptHash(password));
        admin.setRoles(roles);
        admin.setActive(true);

        Admin savedAdmin = adminRepository.save(admin);
        Log.infof("Created new admin user %s (username: %s, roles: %s)",
                savedAdmin.getId(), savedAdmin.getUsername(), savedAdmin.getRoles());

        return savedAdmin;
    }

    @Override
    public Admin updateAdminRoles(UUID adminId, Set<Role> newRoles, UUID currentUserId) {
        // Validate input
        if (newRoles == null || newRoles.isEmpty()) {
            throw new IllegalArgumentException("At least one role is required");
        }

        // Validate role combination
        String roleError = roleValidator.validateRoleCombination(newRoles);
        if (roleError != null) {
            throw new IllegalArgumentException(roleError);
        }

        // Find the admin
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Admin user not found"));

        // Prevent self-demotion from SUPER_ADMIN
        if (currentUserId.equals(adminId)) {
            if (admin.getRoles().contains(Role.SUPER_ADMIN) &&
                    !newRoles.contains(Role.SUPER_ADMIN)) {
                throw new IllegalArgumentException("Cannot remove SUPER_ADMIN role from yourself");
            }
        }

        // Check if this would remove the last SUPER_ADMIN
        if (admin.getRoles().contains(Role.SUPER_ADMIN) &&
                !newRoles.contains(Role.SUPER_ADMIN)) {
            long superAdminCount = countActiveSuperAdmins();

            if (superAdminCount <= 1) {
                throw new IllegalArgumentException("Cannot remove SUPER_ADMIN role from the last super admin");
            }
        }

        Set<Role> oldRoles = admin.getRoles();
        admin.setRoles(newRoles);
        Admin updated = adminRepository.save(admin);

        Log.infof("Updated roles for admin %s (username: %s) from %s to %s",
                adminId, admin.getUsername(), oldRoles, newRoles);

        return updated;
    }

    @Override
    public String resetAdminPassword(UUID adminId) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Admin user not found"));

        String tempPassword = generateTemporaryPassword();
        admin.setPasswordHash(BcryptUtil.bcryptHash(tempPassword));
        adminRepository.save(admin);

        Log.infof("Reset password for admin %s (username: %s)",
                adminId, admin.getUsername());

        return tempPassword;
    }

    @Override
    public Admin deactivateAdmin(UUID adminId, UUID currentUserId) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Admin user not found"));

        // Prevent self-deactivation
        if (currentUserId.equals(adminId)) {
            throw new IllegalArgumentException("Cannot deactivate your own account");
        }

        // Check if this would deactivate the last SUPER_ADMIN
        if (admin.getRoles().contains(Role.SUPER_ADMIN)) {
            long superAdminCount = countActiveSuperAdmins();

            if (superAdminCount <= 1) {
                throw new IllegalArgumentException("Cannot deactivate the last super admin");
            }
        }

        admin.setActive(false);
        Admin deactivated = adminRepository.save(admin);

        Log.infof("Deactivated admin %s (username: %s)",
                adminId, admin.getUsername());

        return deactivated;
    }

    @Override
    public long countActiveSuperAdmins() {
        return adminRepository.findAll().stream()
                .filter(Admin::isActive)
                .filter(a -> a.getRoles().contains(Role.SUPER_ADMIN))
                .count();
    }

    // Helper methods

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

