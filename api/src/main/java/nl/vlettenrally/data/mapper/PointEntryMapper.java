package nl.vlettenrally.data.mapper;

import nl.vlettenrally.data.domain.PointEntry;
import nl.vlettenrally.data.entity.jpa.PointEntryEntity;

/**
 * Mapper between PointEntry domain and PointEntryEntity (JPA).
 */
public final class PointEntryMapper {

    private PointEntryMapper() {}

    public static PointEntry toDomain(PointEntryEntity entity) {
        if (entity == null) return null;

        PointEntry domain = new PointEntry();
        domain.setId(entity.getId());
        domain.setTeamId(entity.getTeam() != null ? entity.getTeam().getId() : null);
        domain.setGameId(entity.getGame() != null ? entity.getGame().getId() : null);
        domain.setEntryType(entity.getEntryType());
        domain.setOriginalAmount(entity.getOriginalAmount());
        domain.setCurrentAmount(entity.getCurrentAmount());
        domain.setDistanceMeters(entity.getDistanceMeters());
        domain.setSourceAchievementId(entity.getSourceAchievementId());
        domain.setLocation(entity.getLocation());
        domain.setDescription(entity.getDescription());
        domain.setCreatedAt(entity.getCreatedAt());
        domain.setUpdatedAt(entity.getUpdatedAt());
        domain.setCreatedBy(entity.getCreatedBy());
        domain.setEditedAt(entity.getEditedAt());
        domain.setEditedBy(entity.getEditedBy());
        domain.setEditReason(entity.getEditReason());
        return domain;
    }

    /**
     * Convert domain to a new entity. Team and Game relationships must be set separately.
     */
    public static PointEntryEntity toEntity(PointEntry domain) {
        if (domain == null) return null;

        PointEntryEntity entity = new PointEntryEntity();
        if (domain.getId() != null) {
            entity.setId(domain.getId());
        }
        entity.setEntryType(domain.getEntryType());
        entity.setOriginalAmount(domain.getOriginalAmount());
        entity.setCurrentAmount(domain.getCurrentAmount());
        entity.setDistanceMeters(domain.getDistanceMeters());
        entity.setSourceAchievementId(domain.getSourceAchievementId());
        entity.setLocation(domain.getLocation());
        entity.setDescription(domain.getDescription());
        if (domain.getCreatedAt() != null) {
            entity.setCreatedAt(domain.getCreatedAt());
        }
        if (domain.getUpdatedAt() != null) {
            entity.setUpdatedAt(domain.getUpdatedAt());
        }
        entity.setCreatedBy(domain.getCreatedBy());
        entity.setEditedAt(domain.getEditedAt());
        entity.setEditedBy(domain.getEditedBy());
        entity.setEditReason(domain.getEditReason());
        return entity;
    }

    public static void updateEntity(PointEntryEntity entity, PointEntry domain) {
        if (entity == null || domain == null) return;

        entity.setCurrentAmount(domain.getCurrentAmount());
        entity.setDistanceMeters(domain.getDistanceMeters());
        entity.setUpdatedAt(domain.getUpdatedAt());
        entity.setEditedAt(domain.getEditedAt());
        entity.setEditedBy(domain.getEditedBy());
        entity.setEditReason(domain.getEditReason());
    }
}
