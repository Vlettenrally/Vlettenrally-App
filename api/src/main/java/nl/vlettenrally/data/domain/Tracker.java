package nl.vlettenrally.data.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A physical GPS tracker in the global pool, with the health status last
 * reported by the location feed. Assignment to a team lives on
 * {@code Team.trackerId}.
 */
@Getter
@Setter
public class Tracker {

    private UUID id;
    private String trackerId;
    /** Disabled trackers are skipped when assigning trackers to teams. */
    private boolean enabled = true;
    private Integer batteryPercentage;
    private Instant lastSeenAt;
    private Double lastLatitude;
    private Double lastLongitude;
    private Instant createdAt;
}
