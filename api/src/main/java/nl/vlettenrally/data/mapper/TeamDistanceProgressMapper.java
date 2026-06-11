package nl.vlettenrally.data.mapper;

import nl.vlettenrally.data.domain.TeamDistanceProgress;
import nl.vlettenrally.data.entity.jpa.TeamDistanceProgressEntity;

/**
 * Mapper between TeamDistanceProgress domain and its JPA entity.
 */
public final class TeamDistanceProgressMapper {

    private TeamDistanceProgressMapper() {}

    public static TeamDistanceProgress toDomain(TeamDistanceProgressEntity entity) {
        if (entity == null) return null;

        TeamDistanceProgress domain = new TeamDistanceProgress();
        domain.setTeamId(entity.getTeamId());
        domain.setAnchorLon(entity.getAnchorLon());
        domain.setAnchorLat(entity.getAnchorLat());
        domain.setCumulativeMeters(entity.getCumulativeMeters());
        domain.setLastProcessedTime(entity.getLastProcessedTime());
        domain.setUpdatedAt(entity.getUpdatedAt());
        return domain;
    }

    public static TeamDistanceProgressEntity toEntity(TeamDistanceProgress domain) {
        if (domain == null) return null;

        TeamDistanceProgressEntity entity = new TeamDistanceProgressEntity();
        entity.setTeamId(domain.getTeamId());
        updateEntity(entity, domain);
        return entity;
    }

    public static void updateEntity(TeamDistanceProgressEntity entity, TeamDistanceProgress domain) {
        if (entity == null || domain == null) return;

        entity.setAnchorLon(domain.getAnchorLon());
        entity.setAnchorLat(domain.getAnchorLat());
        entity.setCumulativeMeters(domain.getCumulativeMeters());
        entity.setLastProcessedTime(domain.getLastProcessedTime());
        entity.setUpdatedAt(domain.getUpdatedAt());
    }
}
