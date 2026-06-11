package nl.vlettenrally.data.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-team incremental accumulator for travelled distance computed from stored
 * location fixes.
 * <p>
 * {@code anchorLon}/{@code anchorLat} is the last <em>counted</em> fix — distance
 * only accrues once a new fix is at least the min-segment threshold away, so GPS
 * jitter at a standstill is ignored while the anchor is held. {@code cumulativeMeters}
 * is the running total fed to the automation engine. {@code lastProcessedTime} is
 * the cursor: the GPS timestamp of the last fix consumed, so each poll only reads
 * newer fixes.
 * <p>
 * {@code anchorLon}/{@code anchorLat}/{@code lastProcessedTime} are null until the
 * first fix is seen.
 */
@Getter
@Setter
public class TeamDistanceProgress {
    private UUID teamId;
    private Double anchorLon;
    private Double anchorLat;
    private double cumulativeMeters;
    private Instant lastProcessedTime;
    private Instant updatedAt;

    public TeamDistanceProgress() {
        this.updatedAt = Instant.now();
    }

    /** True once a first fix has seeded the anchor. */
    public boolean hasAnchor() {
        return anchorLon != null && anchorLat != null;
    }
}
