package nl.vlettenrally.service;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import nl.vlettenrally.data.domain.Admin;
import nl.vlettenrally.data.repository.AdminRepository;
import org.jboss.logging.Logger;

/**
 * Ensures an initial admin user exists at startup.
 */
@ApplicationScoped
public class AdminUserInitializer {
    private static final Logger LOG = Logger.getLogger(AdminUserInitializer.class);
    private static final String DEFAULT_ADMIN_EMAIL = "admin@example.com";


    @Inject
    AdminRepository adminRepository;

    @ConfigProperty(name = "vlettenrally.admin.initial.username", defaultValue = "admin")
    String initialAdminUsername;

    @ConfigProperty(name = "vlettenrally.admin.initial.password", defaultValue = "changeme")
    String initialAdminPassword;

    @PostConstruct
    void ensureAdminUserExists() {
        if (adminRepository.findByUsername(initialAdminUsername).isEmpty()) {
            LOG.info("No admin user found, creating default admin user.");
            Admin admin = new Admin();
            admin.setUsername(initialAdminUsername);
            admin.setPasswordHash(BcryptUtil.bcryptHash(initialAdminPassword));
            admin.setEmail(DEFAULT_ADMIN_EMAIL);
            admin.setActive(true);
            adminRepository.save(admin);
            LOG.warnf("Default admin user created with username '%s' and password '%s'. Please change this password immediately!", initialAdminUsername, initialAdminPassword);
        } else {
            LOG.info("Admin user already exists. No action taken.");
        }
    }
}
