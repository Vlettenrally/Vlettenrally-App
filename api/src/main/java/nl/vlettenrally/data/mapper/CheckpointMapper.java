package nl.vlettenrally.data.mapper;

import nl.vlettenrally.data.domain.Checkpoint;
import nl.vlettenrally.data.entity.jpa.CheckpointEntity;

/**
 * Mapper for converting between Checkpoint domain objects and JPA entities.
 */
public class CheckpointMapper {

    private CheckpointMapper() {
        // Utility class
    }

    /**
     * Convert a JPA entity to a domain object.
     */
    public static Checkpoint toDomain(CheckpointEntity entity) {
        if (entity == null) {
            return null;
        }

        Checkpoint domain = new Checkpoint();
        domain.setId(entity.getId());
        domain.setGameId(entity.getGame() != null ? entity.getGame().getId() : null);
        domain.setName(entity.getName());
        domain.setOpeningTime(entity.getOpeningTime());
        domain.setClosingTime(entity.getClosingTime());
        domain.setPoints(entity.getPoints());
        domain.setLatitude(entity.getLatitude());
        domain.setLongitude(entity.getLongitude());
        domain.setLocation(entity.getLocation());
        domain.setCreatedAt(entity.getCreatedAt());
        domain.setUpdatedAt(entity.getUpdatedAt());

        return domain;
    }

    /**
     * Convert a domain object to a JPA entity.
     * Note: Game relationship must be set separately.
     */
    public static CheckpointEntity toEntity(Checkpoint domain) {
        if (domain == null) {
            return null;
        }

        CheckpointEntity entity = new CheckpointEntity();
        entity.setId(domain.getId());
        entity.setName(domain.getName());
        entity.setOpeningTime(domain.getOpeningTime());
        entity.setClosingTime(domain.getClosingTime());
        entity.setPoints(domain.getPoints());
        entity.setLatitude(domain.getLatitude());
        entity.setLongitude(domain.getLongitude());
        entity.setLocation(domain.getLocation());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());

        return entity;
    }

    /**
     * Update an existing JPA entity with values from a domain object.
     */
    public static void updateEntity(CheckpointEntity entity, Checkpoint domain) {
        if (entity == null || domain == null) {
            return;
        }

        entity.setName(domain.getName());
        entity.setOpeningTime(domain.getOpeningTime());
        entity.setClosingTime(domain.getClosingTime());
        entity.setPoints(domain.getPoints());
        entity.setLatitude(domain.getLatitude());
        entity.setLongitude(domain.getLongitude());
        entity.setLocation(domain.getLocation());
        entity.setUpdatedAt(domain.getUpdatedAt());
    }
}
