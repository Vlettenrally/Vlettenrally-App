package nl.vlettenrally.data.mapper;

import nl.vlettenrally.data.domain.TeamDistanceSegment;
import nl.vlettenrally.data.entity.jpa.TeamDistanceSegmentEntity;

/**
 * Mapper between TeamDistanceSegment domain and its JPA entity. Segments are
 * immutable (insert/delete only), so there is no updateEntity.
 */
public final class TeamDistanceSegmentMapper {

    private TeamDistanceSegmentMapper() {}

    public static TeamDistanceSegment toDomain(TeamDistanceSegmentEntity entity) {
        if (entity == null) return null;

        TeamDistanceSegment domain = new TeamDistanceSegment();
        domain.setId(entity.getId());
        domain.setTeamId(entity.getTeamId());
        domain.setFromTime(entity.getFromTime());
        domain.setFromLon(entity.getFromLon());
        domain.setFromLat(entity.getFromLat());
        domain.setToTime(entity.getToTime());
        domain.setToLon(entity.getToLon());
        domain.setToLat(entity.getToLat());
        domain.setMeters(entity.getMeters());
        domain.setCreatedAt(entity.getCreatedAt());
        return domain;
    }

    public static TeamDistanceSegmentEntity toEntity(TeamDistanceSegment domain) {
        if (domain == null) return null;

        TeamDistanceSegmentEntity entity = new TeamDistanceSegmentEntity();
        entity.setId(domain.getId());
        entity.setTeamId(domain.getTeamId());
        entity.setFromTime(domain.getFromTime());
        entity.setFromLon(domain.getFromLon());
        entity.setFromLat(domain.getFromLat());
        entity.setToTime(domain.getToTime());
        entity.setToLon(domain.getToLon());
        entity.setToLat(domain.getToLat());
        entity.setMeters(domain.getMeters());
        entity.setCreatedAt(domain.getCreatedAt());
        return entity;
    }
}
