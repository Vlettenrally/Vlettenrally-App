package nl.vlettenrally.data.domain;

import lombok.Getter;
import lombok.Setter;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.UUID;

/**
 * Abstract base class for rally checkpoints.
 * <p>
 * Subclasses define the zone shape used for geofencing:
 * <ul>
 *     <li>{@link CircleCheckpoint} — radius-based circle zone</li>
 *     <li>{@link PolygonCheckpoint} — arbitrary polygon zone (PostGIS geometry)</li>
 * </ul>
 */
@Setter
@Getter
public abstract sealed class Checkpoint permits CircleCheckpoint, PolygonCheckpoint {

    private UUID id;
    private UUID gameId;
    private String name;
    private Instant openingTime;
    private Instant closingTime;
    private int points;
    private Double latitude;
    private Double longitude;
    private Point location;       // PostGIS Point for geofencing
    private String externalId;    // External ID from location tracker
    private boolean hidden;       // Hidden from teams' map, but still triggers automations
    private String color;         // Hex color for map display, e.g. "#ff9800"; null = default orange
    private Instant createdAt;
    private Instant updatedAt;

    protected Checkpoint() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Returns the zone type discriminator string used for persistence.
     */
    public abstract String getZoneType();
}
