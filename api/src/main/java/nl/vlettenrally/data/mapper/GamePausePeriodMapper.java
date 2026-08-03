package nl.vlettenrally.data.mapper;

import nl.vlettenrally.data.domain.GamePausePeriod;
import nl.vlettenrally.data.entity.jpa.GamePausePeriodEntity;

/**
 * Mapper between GamePausePeriod domain and its JPA entity. Resuming is the only
 * mutation a period ever sees, so {@link #applyResume} is the whole update surface.
 */
public final class GamePausePeriodMapper {

    private GamePausePeriodMapper() {}

    public static GamePausePeriod toDomain(GamePausePeriodEntity entity) {
        if (entity == null) return null;

        GamePausePeriod domain = new GamePausePeriod();
        domain.setId(entity.getId());
        domain.setGameId(entity.getGameId());
        domain.setPausedAt(entity.getPausedAt());
        domain.setResumedAt(entity.getResumedAt());
        domain.setReason(entity.getReason());
        domain.setPausedBy(entity.getPausedBy());
        domain.setCreatedAt(entity.getCreatedAt());
        return domain;
    }

    public static GamePausePeriodEntity toEntity(GamePausePeriod domain) {
        if (domain == null) return null;

        GamePausePeriodEntity entity = new GamePausePeriodEntity();
        entity.setId(domain.getId());
        entity.setGameId(domain.getGameId());
        entity.setPausedAt(domain.getPausedAt());
        entity.setResumedAt(domain.getResumedAt());
        entity.setReason(domain.getReason());
        entity.setPausedBy(domain.getPausedBy());
        entity.setCreatedAt(domain.getCreatedAt());
        return entity;
    }

    public static void applyResume(GamePausePeriodEntity entity, GamePausePeriod domain) {
        entity.setResumedAt(domain.getResumedAt());
    }
}
