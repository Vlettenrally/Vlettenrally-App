package nl.vlettenrally.data.domain;

import lombok.Getter;
import lombok.Setter;
import org.locationtech.jts.geom.Polygon;

/**
 * A checkpoint with a polygon-shaped geofence zone defined by a PostGIS polygon geometry.
 */
@Setter
@Getter
public final class PolygonCheckpoint extends Checkpoint {

    private Polygon zonePolygon;

    public PolygonCheckpoint() {
        super();
    }

    public PolygonCheckpoint(Polygon zonePolygon) {
        super();
        this.zonePolygon = zonePolygon;
    }

    @Override
    public String getZoneType() {
        return "polygon";
    }
}

