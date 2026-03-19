package nl.vlettenrally.data.mapper;

import nl.vlettenrally.data.domain.RefreshToken;
import nl.vlettenrally.data.entity.jpa.RefreshTokenEntity;

/**
 * Mapper for converting between RefreshToken domain objects and JPA entities.
 */
public class RefreshTokenMapper {

    private RefreshTokenMapper() {
        // Utility class
    }

    /**
     * Convert a JPA entity to a domain object.
     */
    public static RefreshToken toDomain(RefreshTokenEntity entity) {
        if (entity == null) {
            return null;
        }

        RefreshToken domain = new RefreshToken();
        domain.setId(entity.getId());
        domain.setToken(entity.getToken());
        domain.setSubject(entity.getSubject());
        domain.setName(entity.getName());
        domain.setRoles(entity.getRoles() != null ? entity.getRoles() : new java.util.HashSet<>());
        domain.setExpiresAt(entity.getExpiresAt());
        domain.setRevoked(entity.isRevoked());
        domain.setRevokedAt(entity.getRevokedAt());
        domain.setCreatedAt(entity.getCreatedAt());
        domain.setUpdatedAt(entity.getUpdatedAt());

        return domain;
    }

    /**
     * Convert a domain object to a JPA entity.
     */
    public static RefreshTokenEntity toEntity(RefreshToken domain) {
        if (domain == null) {
            return null;
        }

        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setId(domain.getId());
        entity.setToken(domain.getToken());
        entity.setSubject(domain.getSubject());
        entity.setName(domain.getName());
        entity.setRoles(domain.getRoles() != null ? domain.getRoles() : new java.util.HashSet<>());
        entity.setExpiresAt(domain.getExpiresAt());
        entity.setRevoked(domain.isRevoked());
        entity.setRevokedAt(domain.getRevokedAt());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());

        return entity;
    }

    /**
     * Update an existing entity with values from a domain object.
     */
    public static void updateEntity(RefreshTokenEntity entity, RefreshToken domain) {
        if (entity == null || domain == null) {
            return;
        }

        entity.setToken(domain.getToken());
        entity.setSubject(domain.getSubject());
        entity.setName(domain.getName());
        entity.setRoles(domain.getRoles() != null ? domain.getRoles() : new java.util.HashSet<>());
        entity.setExpiresAt(domain.getExpiresAt());
        entity.setRevoked(domain.isRevoked());
        entity.setRevokedAt(domain.getRevokedAt());
    }
}
