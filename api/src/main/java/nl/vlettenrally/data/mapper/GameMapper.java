package nl.vlettenrally.data.mapper;

import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.entity.jpa.GameEntity;

/**
 * Mapper for converting between Game domain objects and JPA entities.
 */
public class GameMapper {

    private GameMapper() {
        // Utility class
    }

    /**
     * Convert a JPA entity to a domain object.
     */
    public static Game toDomain(GameEntity entity) {
        if (entity == null) {
            return null;
        }

        Game domain = new Game();
        domain.setId(entity.getId());
        domain.setName(entity.getName());
        domain.setDescription(entity.getDescription());
        domain.setStartTime(entity.getStartTime());
        domain.setEndTime(entity.getEndTime());
        domain.setStartingAreaActiveTime(entity.getStartingAreaActiveTime());
        domain.setStartSignalTime(entity.getStartSignalTime());
        domain.setStatus(mapStatus(entity.getStatus()));
        domain.setCreatedAt(entity.getCreatedAt());
        domain.setUpdatedAt(entity.getUpdatedAt());

        return domain;
    }

    /**
     * Convert a domain object to a JPA entity.
     */
    public static GameEntity toEntity(Game domain) {
        if (domain == null) {
            return null;
        }

        GameEntity entity = new GameEntity();
        entity.setId(domain.getId());
        entity.setName(domain.getName());
        entity.setDescription(domain.getDescription());
        entity.setStartTime(domain.getStartTime());
        entity.setEndTime(domain.getEndTime());
        entity.setStartingAreaActiveTime(domain.getStartingAreaActiveTime());
        entity.setStartSignalTime(domain.getStartSignalTime());
        entity.setStatus(mapStatus(domain.getStatus()));
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());

        return entity;
    }

    /**
     * Update an existing JPA entity with values from a domain object.
     */
    public static void updateEntity(GameEntity entity, Game domain) {
        if (entity == null || domain == null) {
            return;
        }

        entity.setName(domain.getName());
        entity.setDescription(domain.getDescription());
        entity.setStartTime(domain.getStartTime());
        entity.setEndTime(domain.getEndTime());
        entity.setStartingAreaActiveTime(domain.getStartingAreaActiveTime());
        entity.setStartSignalTime(domain.getStartSignalTime());
        entity.setStatus(mapStatus(domain.getStatus()));
        entity.setUpdatedAt(domain.getUpdatedAt());
    }

    private static Game.GameStatus mapStatus(GameEntity.GameStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case PLANNED -> Game.GameStatus.PLANNED;
            case ACTIVE -> Game.GameStatus.ACTIVE;
            case COMPLETED -> Game.GameStatus.COMPLETED;
        };
    }

    private static GameEntity.GameStatus mapStatus(Game.GameStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case PLANNED -> GameEntity.GameStatus.PLANNED;
            case ACTIVE -> GameEntity.GameStatus.ACTIVE;
            case COMPLETED -> GameEntity.GameStatus.COMPLETED;
        };
    }
}
