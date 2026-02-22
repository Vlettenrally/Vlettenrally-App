package nl.vlettenrally.data.entity.jpa;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for LocationDataEntity.
 * Required by TimescaleDB hypertables which partition by time column.
 *
 * The key consists of:
 * - id: Unique identifier for the location record
 * - locationTime: Timestamp for TimescaleDB partitioning
 */
public class LocationDataId implements Serializable {

    private UUID id;
    private Instant locationTime;

    public LocationDataId() {
    }

    public LocationDataId(UUID id, Instant locationTime) {
        this.id = id;
        this.locationTime = locationTime;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Instant getLocationTime() {
        return locationTime;
    }

    public void setLocationTime(Instant locationTime) {
        this.locationTime = locationTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocationDataId that = (LocationDataId) o;
        return Objects.equals(id, that.id) &&
               Objects.equals(locationTime, that.locationTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, locationTime);
    }

    @Override
    public String toString() {
        return "LocationDataId{" +
                "id=" + id +
                ", locationTime=" + locationTime +
                '}';
    }
}

