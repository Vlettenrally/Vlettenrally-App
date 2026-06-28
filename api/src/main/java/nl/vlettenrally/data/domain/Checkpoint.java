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
     * Whether the checkpoint is open (scoring/automations active) at the given
     * instant. Null bounds are open-ended: a null opening time means open from
     * the start, a null closing time means open until the end, both null means
     * always open. Boundary instants count as open. A null instant (unknown pass
     * time) is treated as open so the window never suppresses on missing data.
     */
    public boolean isOpenAt(Instant t) {
        if (t == null) return true;
        if (openingTime != null && t.isBefore(openingTime)) return false;
        if (closingTime != null && t.isAfter(closingTime)) return false;
        return true;
    }

    /**
     * Returns the zone type discriminator string used for persistence.
     */
    public abstract String getZoneType();
}
