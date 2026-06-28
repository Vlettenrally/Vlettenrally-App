package nl.vlettenrally.data.repository.mock;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import nl.vlettenrally.data.domain.Admin;
import nl.vlettenrally.data.enums.Role;
import nl.vlettenrally.data.repository.AdminRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock implementation of AdminRepository for development/testing.
 * Only active in dev and test profiles - replaced by PostgresAdminRepository in prod.
 */
@IfBuildProfile(anyOf = {"dev", "test"})
@ApplicationScoped
public class MockAdminRepository implements AdminRepository {
    
    private final Map<UUID, Admin> admins = new ConcurrentHashMap<>();
    
    // Well-known UUIDs for mock admins
    public static final UUID ADMIN_DEFAULT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID RALLY_DESK_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    
    @PostConstruct
    void init() {
        initializeData();
    }
    
    public void initializeData() {
        admins.clear();
        
        // Create default admin with bcrypt-hashed password "admin123" and both ADMIN and SUPER_ADMIN roles
        Admin defaultAdmin = new Admin();
        defaultAdmin.setId(ADMIN_DEFAULT_ID);
        defaultAdmin.setUsername("admin");
        defaultAdmin.setPasswordHash(BcryptUtil.bcryptHash("admin123"));
        defaultAdmin.setRoles(Set.of(Role.ADMIN, Role.SUPER_ADMIN));
        defaultAdmin.setActive(true);
        admins.put(defaultAdmin.getId(), defaultAdmin);

        Admin rallyDeskAdmin = new Admin();
        rallyDeskAdmin.setId(RALLY_DESK_ID);
        rallyDeskAdmin.setUsername("rallydesk");
        rallyDeskAdmin.setPasswordHash(BcryptUtil.bcryptHash("rallydesk123"));
        rallyDeskAdmin.setRoles(Set.of(Role.RALLY_DESK));
        rallyDeskAdmin.setActive(true);
        admins.put(rallyDeskAdmin.getId(), rallyDeskAdmin);

        Log.info("🎭 MockAdminRepository initialized with " + admins.size() + " admin(s)");
        Log.info("   Default admin credentials: admin / admin123");
        Log.info("   Rally desk credentials: rallydesk / rallydesk123");
    }
    
    @Override
    public Optional<Admin> findById(UUID id) {
        return Optional.ofNullable(admins.get(id));
    }
    
    @Override
    public Optional<Admin> findByUsername(String username) {
        return admins.values().stream()
            .filter(admin -> admin.getUsername().equalsIgnoreCase(username))
            .findFirst();
    }
    
    @Override
    public List<Admin> findAll() {
        return List.copyOf(admins.values());
    }
    
    @Override
    public Admin save(Admin admin) {
        admin.setUpdatedAt(java.time.Instant.now());
        admins.put(admin.getId(), admin);
        return admin;
    }
    
    @Override
    public void delete(UUID id) {
        admins.remove(id);
    }
}
