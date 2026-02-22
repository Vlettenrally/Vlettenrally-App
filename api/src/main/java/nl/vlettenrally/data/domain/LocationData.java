package nl.vlettenrally.data.domain;

import lombok.Getter;
import lombok.Setter;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain model for location data.
 * Represents a single location update from a team's tracker.
 */
@Getter
@Setter
public class LocationData {
    private UUID id;
    private UUID teamId;
    private UUID gameId;
    private Point location;  // PostGIS Point with WGS84 coordinates (longitude, latitude)
    private Instant locationTime;  // API's location_time field
    private BigDecimal accuracy;  // GPS accuracy in meters
    private BigDecimal altitude;  // Altitude in meters
    private BigDecimal speed;  // Speed in km/h
    private BigDecimal heading;  // Heading/bearing in degrees
    private Instant createdAt;

    public LocationData() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }

    public LocationData(UUID id, UUID teamId, UUID gameId, Point location, Instant locationTime,
                       BigDecimal accuracy, BigDecimal altitude, BigDecimal speed, BigDecimal heading) {
        this.id = id;
        this.teamId = teamId;
        this.gameId = gameId;
        this.location = location;
        this.locationTime = locationTime;
        this.accuracy = accuracy;
        this.altitude = altitude;
        this.speed = speed;
        this.heading = heading;
        this.createdAt = Instant.now();
    }
}

