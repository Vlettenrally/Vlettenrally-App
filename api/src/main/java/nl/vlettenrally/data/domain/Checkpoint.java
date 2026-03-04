package nl.vlettenrally.data.domain;

import lombok.Getter;
import lombok.Setter;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.UUID;

/**
 * Checkpoint entity representing a rally checkpoint
 */
@Setter
@Getter
public class Checkpoint {
    // Getters and Setters
    private UUID id;
    private UUID gameId;
    private String name;
    private Instant openingTime;
    private Instant closingTime;
    private int points;
    private Double latitude;
    private Double longitude;
    private Point location;  // PostGIS Point for geofencing (50m radius)
    private Instant createdAt;
    private Instant updatedAt;

    public Checkpoint() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Checkpoint(UUID id, UUID gameId, String name, Instant openingTime, int points) {
        this();
        this.id = id;
        this.gameId = gameId;
        this.name = name;
        this.openingTime = openingTime;
        this.points = points;
    }

}
