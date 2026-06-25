package nl.vlettenrally.api.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;

/**
 * Unit tests for {@link StaffRoleAugmentor}: any admin-type role earns the synthetic
 * {@code staff} role; team-only and anonymous identities do not.
 */
class StaffRoleAugmentorTest {

    private final StaffRoleAugmentor augmentor = new StaffRoleAugmentor();

    private SecurityIdentity augment(SecurityIdentity identity) {
        return augmentor.augment(identity, null).await().atMost(Duration.ofSeconds(2));
    }

    private SecurityIdentity withRoles(String... roles) {
        QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder()
            .setPrincipal(new QuarkusPrincipal("user-id"));
        for (String role : roles) {
            builder.addRole(role);
        }
        return builder.build();
    }

    @Test
    void gameMaster_gainsStaffRole() {
        SecurityIdentity result = augment(withRoles(SecurityRoles.GAME_MASTER));
        assertTrue(result.hasRole(SecurityRoles.STAFF));
    }

    @Test
    void viewer_gainsStaffRole() {
        SecurityIdentity result = augment(withRoles(SecurityRoles.VIEWER));
        assertTrue(result.hasRole(SecurityRoles.STAFF));
    }

    @Test
    void admin_gainsStaffRole() {
        SecurityIdentity result = augment(withRoles(SecurityRoles.ADMIN));
        assertTrue(result.hasRole(SecurityRoles.STAFF));
    }

    @Test
    void team_doesNotGainStaffRole() {
        SecurityIdentity result = augment(withRoles(SecurityRoles.TEAM));
        assertFalse(result.hasRole(SecurityRoles.STAFF));
    }

    @Test
    void anonymous_doesNotGainStaffRole() {
        SecurityIdentity anonymous = QuarkusSecurityIdentity.builder().setAnonymous(true).build();
        SecurityIdentity result = augment(anonymous);
        assertFalse(result.hasRole(SecurityRoles.STAFF));
    }
}
