package nl.vlettenrally.api.security;

import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Adds the synthetic {@link SecurityRoles#STAFF} role to any authenticated identity that holds
 * at least one admin-type role ({@link SecurityRoles#ADMIN_ROLES}).
 * <p>
 * This lets endpoints and filters that mean "any admin/staff user" (websocket connect, reading
 * data, self-management) check a single role instead of enumerating every admin role. Granular
 * write endpoints keep checking their specific capability role.
 * <p>
 * Runs for every authenticated request, so {@code @RolesAllowed}, {@code isUserInRole} and
 * {@code hasRole} all observe the derived role.
 */
@ApplicationScoped
public class StaffRoleAugmentor implements SecurityIdentityAugmentor {

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        if (identity.isAnonymous()) {
            return Uni.createFrom().item(identity);
        }

        boolean hasAdminRole = identity.getRoles().stream().anyMatch(SecurityRoles.ADMIN_ROLES::contains);
        if (!hasAdminRole) {
            return Uni.createFrom().item(identity);
        }

        SecurityIdentity augmented = QuarkusSecurityIdentity.builder(identity)
            .addRole(SecurityRoles.STAFF)
            .build();
        return Uni.createFrom().item(augmented);
    }
}
