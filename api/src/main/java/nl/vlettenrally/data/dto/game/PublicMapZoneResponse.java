package nl.vlettenrally.data.dto.game;

import io.quarkus.runtime.annotations.RegisterForReflection;
import nl.vlettenrally.data.domain.Checkpoint;
import nl.vlettenrally.data.domain.CircleCheckpoint;
import nl.vlettenrally.data.domain.PolygonCheckpoint;
import nl.vlettenrally.data.mapper.CheckpointMapper;

import java.util.List;
import java.util.UUID;

/**
 * A zone as drawn on the public narrowcasting map.
 * <p>
 * A deliberately minimal projection: unlike {@link CheckpointResponse} it omits {@code externalId}
 * and {@code points} (operational detail nobody watching a display needs) and — most importantly —
 * the {@code hidden} flag. Hidden zones are filtered out before this record is ever built, and
 * leaving the field off the wire entirely means there is no way to leak their existence even by
 * accident.
 */
@RegisterForReflection
public record PublicMapZoneResponse(
    UUID id,
    String name,
    Double latitude,
    Double longitude,
    String zoneType,
    double radiusMeters,
    /* Polygon vertices as [lat, lng] pairs; null for circle zones. Closing vertex omitted. */
    List<List<Double>> polygonPoints,
    String openingTime,
    String closingTime,
    String color
) {
    public static PublicMapZoneResponse from(Checkpoint checkpoint) {
        double radiusMeters = 0;
        List<List<Double>> polygonPoints = null;

        if (checkpoint instanceof CircleCheckpoint circle) {
            radiusMeters = circle.getRadiusMeters();
        } else if (checkpoint instanceof PolygonCheckpoint polygon) {
            polygonPoints = CheckpointMapper.polygonToLatLngPairs(polygon.getZonePolygon());
        }

        return new PublicMapZoneResponse(
            checkpoint.getId(),
            checkpoint.getName(),
            checkpoint.getLatitude(),
            checkpoint.getLongitude(),
            checkpoint.getZoneType(),
            radiusMeters,
            polygonPoints,
            checkpoint.getOpeningTime() != null ? checkpoint.getOpeningTime().toString() : null,
            checkpoint.getClosingTime() != null ? checkpoint.getClosingTime().toString() : null,
            checkpoint.getColor()
        );
    }
}
