package nl.vlettenrally.data.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * A checkpoint with a circle-shaped geofence zone defined by a radius in meters.
 */
@Setter
@Getter
public final class CircleCheckpoint extends Checkpoint {

    private double radiusMeters = 50.0;

    public CircleCheckpoint() {
        super();
    }

    public CircleCheckpoint(double radiusMeters) {
        super();
        this.radiusMeters = radiusMeters;
    }

    @Override
    public String getZoneType() {
        return "circle";
    }
}

