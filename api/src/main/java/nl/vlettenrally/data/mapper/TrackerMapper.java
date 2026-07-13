package nl.vlettenrally.data.mapper;

import nl.vlettenrally.data.domain.Tracker;
import nl.vlettenrally.data.entity.jpa.TrackerEntity;

/**
 * Mapper for converting between Tracker domain objects and JPA entities.
 */
public class TrackerMapper {

    private TrackerMapper() {
        // Utility class
    }

    public static Tracker toDomain(TrackerEntity entity) {
        if (entity == null) {
            return null;
        }

        Tracker domain = new Tracker();
        domain.setId(entity.getId());
        domain.setTrackerId(entity.getTrackerId());
        domain.setEnabled(entity.isEnabled());
        domain.setBatteryPercentage(entity.getBatteryPercentage());
        domain.setLastSeenAt(entity.getLastSeenAt());
        domain.setLastLatitude(entity.getLastLatitude());
        domain.setLastLongitude(entity.getLastLongitude());
        domain.setCreatedAt(entity.getCreatedAt());
        return domain;
    }

    public static TrackerEntity toEntity(Tracker domain) {
        if (domain == null) {
            return null;
        }

        TrackerEntity entity = new TrackerEntity();
        if (domain.getId() != null) {
            entity.setId(domain.getId());
        }
        entity.setTrackerId(domain.getTrackerId());
        entity.setEnabled(domain.isEnabled());
        entity.setBatteryPercentage(domain.getBatteryPercentage());
        entity.setLastSeenAt(domain.getLastSeenAt());
        entity.setLastLatitude(domain.getLastLatitude());
        entity.setLastLongitude(domain.getLastLongitude());
        if (domain.getCreatedAt() != null) {
            entity.setCreatedAt(domain.getCreatedAt());
        }
        return entity;
    }
}
