package nl.vlettenrally.data.mapper;

import nl.vlettenrally.data.domain.Admin;
import nl.vlettenrally.data.entity.jpa.AdminEntity;

/**
 * Mapper for converting between Admin domain objects and JPA entities.
 */
public class AdminMapper {

    private AdminMapper() {
        // Utility class
    }

    /**
     * Convert a JPA entity to a domain object.
     */
    public static Admin toDomain(AdminEntity entity) {
        if (entity == null) {
            return null;
        }

        Admin domain = new Admin();
        domain.setId(entity.getId());
        domain.setUsername(entity.getUsername());
        domain.setPasswordHash(entity.getPasswordHash());
        domain.setEmail(entity.getEmail());
        domain.setActive(entity.isActive());
        domain.setCreatedAt(entity.getCreatedAt());
        domain.setUpdatedAt(entity.getUpdatedAt());

        return domain;
    }

    /**
     * Convert a domain object to a JPA entity.
     */
    public static AdminEntity toEntity(Admin domain) {
        if (domain == null) {
            return null;
        }

        AdminEntity entity = new AdminEntity();
        entity.setId(domain.getId());
        entity.setUsername(domain.getUsername());
        entity.setPasswordHash(domain.getPasswordHash());
        entity.setEmail(domain.getEmail());
        entity.setActive(domain.isActive());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());

        return entity;
    }

    /**
     * Update an existing JPA entity with values from a domain object.
     */
    public static void updateEntity(AdminEntity entity, Admin domain) {
        if (entity == null || domain == null) {
            return;
        }

        entity.setUsername(domain.getUsername());
        entity.setPasswordHash(domain.getPasswordHash());
        entity.setEmail(domain.getEmail());
        entity.setActive(domain.isActive());
        entity.setUpdatedAt(domain.getUpdatedAt());
    }
}
