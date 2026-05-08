package nl.vlettenrally.data.mapper;

import nl.vlettenrally.data.domain.Achievement;
import nl.vlettenrally.data.entity.jpa.AchievementEntity;

/**
 * Mapper for converting between Achievement domain objects and JPA entities.
 */
public class AchievementMapper {

    private AchievementMapper() {
        // Utility class
    }

    /**
     * Convert a JPA entity to a domain object.
     */
    public static Achievement toDomain(AchievementEntity entity) {
        if (entity == null) {
            return null;
        }

        Achievement domain = new Achievement();
        domain.setId(entity.getId());
        domain.setTeamId(entity.getTeam() != null ? entity.getTeam().getId() : null);
        domain.setCheckpointId(entity.getCheckpoint() != null ? entity.getCheckpoint().getId() : null);
        domain.setBonusPoints(entity.getBonusPoints());
        domain.setStatus(entity.getStatus());
        domain.setDetectedAt(entity.getDetectedAt());
        domain.setVerifiedAt(entity.getVerifiedAt());
        domain.setVerifiedBy(entity.getVerifiedBy() != null ? entity.getVerifiedBy().getId() : null);
        domain.setNotes(entity.getNotes());

        return domain;
    }

    /**
     * Convert a domain object to a JPA entity.
     * Note: Relationships (team, checkpoint, verifiedBy) must be set separately.
     */
    public static AchievementEntity toEntity(Achievement domain) {
        if (domain == null) {
            return null;
        }

        AchievementEntity entity = new AchievementEntity();
        entity.setId(domain.getId());
        entity.setBonusPoints(domain.getBonusPoints());
        entity.setStatus(domain.getStatus());
        entity.setDetectedAt(domain.getDetectedAt());
        entity.setVerifiedAt(domain.getVerifiedAt());
        entity.setNotes(domain.getNotes());

        return entity;
    }

    /**
     * Update an existing JPA entity with values from a domain object.
     */
    public static void updateEntity(AchievementEntity entity, Achievement domain) {
        if (entity == null || domain == null) {
            return;
        }

        entity.setBonusPoints(domain.getBonusPoints());
        entity.setStatus(domain.getStatus());
        entity.setVerifiedAt(domain.getVerifiedAt());
        entity.setNotes(domain.getNotes());
    }
}
