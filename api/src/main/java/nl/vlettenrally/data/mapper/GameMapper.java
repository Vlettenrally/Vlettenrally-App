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
        domain.setParticipantInfo(entity.getParticipantInfo());
        domain.setStartTime(entity.getStartTime());
        domain.setEndTime(entity.getEndTime());
        domain.setCheckInStartTime(entity.getCheckInStartTime());
        domain.setStartSignalTime(entity.getStartSignalTime());
        domain.setStatus(mapStatus(entity.getStatus()));
        domain.setCreatedAt(entity.getCreatedAt());
        domain.setUpdatedAt(entity.getUpdatedAt());
        
        // External event integration
        domain.setExternalEventId(entity.getExternalEventId());
        domain.setExternalEventName(entity.getExternalEventName());
        
        // Location tracking configuration
        domain.setLocationEndpointUrl(entity.getLocationEndpointUrl());
        domain.setLocationPollingFrequency(entity.getLocationPollingFrequency());
        domain.setLocationEndpointActive(entity.getLocationEndpointActive());

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
        entity.setParticipantInfo(domain.getParticipantInfo());
        entity.setStartTime(domain.getStartTime());
        entity.setEndTime(domain.getEndTime());
        entity.setCheckInStartTime(domain.getCheckInStartTime());
        entity.setStartSignalTime(domain.getStartSignalTime());
        entity.setStatus(mapStatus(domain.getStatus()));
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        
        // External event integration
        entity.setExternalEventId(domain.getExternalEventId());
        entity.setExternalEventName(domain.getExternalEventName());
        
        // Location tracking configuration
        entity.setLocationEndpointUrl(domain.getLocationEndpointUrl());
        entity.setLocationPollingFrequency(domain.getLocationPollingFrequency());
        entity.setLocationEndpointActive(domain.getLocationEndpointActive());

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
        entity.setParticipantInfo(domain.getParticipantInfo());
        entity.setStartTime(domain.getStartTime());
        entity.setEndTime(domain.getEndTime());
        entity.setCheckInStartTime(domain.getCheckInStartTime());
        entity.setStartSignalTime(domain.getStartSignalTime());
        entity.setStatus(mapStatus(domain.getStatus()));
        entity.setUpdatedAt(domain.getUpdatedAt());
        
        // External event integration
        entity.setExternalEventId(domain.getExternalEventId());
        entity.setExternalEventName(domain.getExternalEventName());
        
        // Location tracking configuration
        entity.setLocationEndpointUrl(domain.getLocationEndpointUrl());
        entity.setLocationPollingFrequency(domain.getLocationPollingFrequency());
        entity.setLocationEndpointActive(domain.getLocationEndpointActive());
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
