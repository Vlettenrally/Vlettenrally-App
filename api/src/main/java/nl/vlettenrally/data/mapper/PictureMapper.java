package nl.vlettenrally.data.mapper;

import nl.vlettenrally.data.domain.Picture;
import nl.vlettenrally.data.entity.jpa.PictureEntity;

/**
 * Mapper for converting between Picture domain objects and JPA entities.
 */
public class PictureMapper {

    private PictureMapper() {
        // Utility class
    }

    /**
     * Convert a JPA entity to a domain object.
     */
    public static Picture toDomain(PictureEntity entity) {
        if (entity == null) {
            return null;
        }

        Picture domain = new Picture();
        domain.setId(entity.getId());
        domain.setTeamId(entity.getTeam() != null ? entity.getTeam().getId() : null);
        domain.setGameId(entity.getGame() != null ? entity.getGame().getId() : null);
        domain.setObjectKey(entity.getObjectKey());
        domain.setStatus(entity.getStatus());
        domain.setContentType(entity.getContentType());
        domain.setSizeBytes(entity.getSizeBytes());
        domain.setCreatedAt(entity.getCreatedAt());
        domain.setUploadedAt(entity.getUploadedAt());
        domain.setModeratedAt(entity.getModeratedAt());
        domain.setModeratedBy(entity.getModeratedBy() != null ? entity.getModeratedBy().getId() : null);
        domain.setNotes(entity.getNotes());

        return domain;
    }

    /**
     * Convert a domain object to a JPA entity.
     * Note: Relationships (team, game, moderatedBy) must be set separately.
     */
    public static PictureEntity toEntity(Picture domain) {
        if (domain == null) {
            return null;
        }

        PictureEntity entity = new PictureEntity();
        entity.setId(domain.getId());
        entity.setObjectKey(domain.getObjectKey());
        entity.setStatus(domain.getStatus());
        entity.setContentType(domain.getContentType());
        entity.setSizeBytes(domain.getSizeBytes());
        entity.setUploadedAt(domain.getUploadedAt());
        entity.setModeratedAt(domain.getModeratedAt());
        entity.setNotes(domain.getNotes());

        return entity;
    }

    /**
     * Update an existing JPA entity with values from a domain object.
     * Note: the moderatedBy relationship is resolved in the repository.
     */
    public static void updateEntity(PictureEntity entity, Picture domain) {
        if (entity == null || domain == null) {
            return;
        }

        entity.setStatus(domain.getStatus());
        entity.setContentType(domain.getContentType());
        entity.setSizeBytes(domain.getSizeBytes());
        entity.setUploadedAt(domain.getUploadedAt());
        entity.setModeratedAt(domain.getModeratedAt());
        entity.setNotes(domain.getNotes());
    }
}
