package nl.vlettenrally.data.mapper;

import nl.vlettenrally.data.domain.Checkpoint;
import nl.vlettenrally.data.domain.CircleCheckpoint;
import nl.vlettenrally.data.domain.PolygonCheckpoint;
import nl.vlettenrally.data.entity.jpa.CheckpointEntity;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;

import java.util.ArrayList;
import java.util.List;

/**
 * Mapper for converting between Checkpoint domain objects and JPA entities, plus the shared
 * JTS-geometry-to-wire-format conversion used by every DTO that exposes a zone polygon.
 */
public class CheckpointMapper {

    private CheckpointMapper() {
        // Utility class
    }

    /**
     * Extract polygon vertices as a list of {@code [lat, lng]} pairs from a JTS Polygon — the shape
     * every zone DTO puts on the wire. Excludes the closing point (which duplicates the first), since
     * the map clients close the ring themselves.
     */
    public static List<List<Double>> polygonToLatLngPairs(Polygon polygon) {
        if (polygon == null) {
            return null;
        }
        Coordinate[] coords = polygon.getExteriorRing().getCoordinates();
        List<List<Double>> result = new ArrayList<>();
        // Skip the last coordinate (closing point that duplicates the first)
        for (int i = 0; i < coords.length - 1; i++) {
            result.add(List.of(coords[i].getY(), coords[i].getX())); // [lat, lng]
        }
        return result;
    }

    /**
     * Convert a JPA entity to the appropriate domain subtype.
     */
    public static Checkpoint toDomain(CheckpointEntity entity) {
        if (entity == null) {
            return null;
        }

        Checkpoint domain;
        if ("polygon".equals(entity.getZoneType())) {
            PolygonCheckpoint poly = new PolygonCheckpoint();
            poly.setZonePolygon(entity.getZonePolygon());
            domain = poly;
        } else {
            CircleCheckpoint circle = new CircleCheckpoint();
            circle.setRadiusMeters(entity.getRadiusMeters());
            domain = circle;
        }

        mapCommonFields(entity, domain);
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
        entity.setZoneType(domain.getZoneType());
        setZoneSpecificFields(entity, domain);
        setCommonEntityFields(entity, domain);
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

        entity.setZoneType(domain.getZoneType());
        setZoneSpecificFields(entity, domain);
        setCommonEntityFields(entity, domain);
        entity.setUpdatedAt(domain.getUpdatedAt());
    }

    private static void mapCommonFields(CheckpointEntity entity, Checkpoint domain) {
        domain.setId(entity.getId());
        domain.setGameId(entity.getGame() != null ? entity.getGame().getId() : null);
        domain.setName(entity.getName());
        domain.setOpeningTime(entity.getOpeningTime());
        domain.setClosingTime(entity.getClosingTime());
        domain.setPoints(entity.getPoints());
        domain.setLatitude(entity.getLatitude());
        domain.setLongitude(entity.getLongitude());
        domain.setLocation(entity.getLocation());
        domain.setExternalId(entity.getExternalId());
        domain.setHidden(entity.isHidden());
        domain.setColor(entity.getColor());
        domain.setCreatedAt(entity.getCreatedAt());
        domain.setUpdatedAt(entity.getUpdatedAt());
    }

    private static void setCommonEntityFields(CheckpointEntity entity, Checkpoint domain) {
        entity.setName(domain.getName());
        entity.setOpeningTime(domain.getOpeningTime());
        entity.setClosingTime(domain.getClosingTime());
        entity.setPoints(domain.getPoints());
        entity.setLatitude(domain.getLatitude());
        entity.setLongitude(domain.getLongitude());
        entity.setLocation(domain.getLocation());
        entity.setExternalId(domain.getExternalId());
        entity.setHidden(domain.isHidden());
        entity.setColor(domain.getColor());
    }

    private static void setZoneSpecificFields(CheckpointEntity entity, Checkpoint domain) {
        if (domain instanceof CircleCheckpoint circle) {
            entity.setRadiusMeters(circle.getRadiusMeters());
            entity.setZonePolygon(null);
        } else if (domain instanceof PolygonCheckpoint polygon) {
            entity.setZonePolygon(polygon.getZonePolygon());
            entity.setRadiusMeters(0.0);
        }
    }
}
