package nl.vlettenrally.data.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One counted movement segment between two location fixes of a team. Segments form
 * a per-team chain: each row's {@code from} is the previous row's {@code to}, the
 * last row's endpoint is the anchor (last counted fix), and the team's travelled
 * distance is the sum of {@code meters}. Rows are immutable — the chain is only
 * appended to, or has its tail deleted and rebuilt after a backfill.
 * <p>
 * A team's first row is a <em>seed</em>: {@code fromTime}/{@code fromLon}/{@code fromLat}
 * are null and it carries no distance of its own ({@code meters} 0), except for the
 * migration carryover seed whose {@code meters} preserve the pre-segment-table total.
 */
@Getter
@Setter
public class TeamDistanceSegment {
    private UUID id;
    private UUID teamId;
    private Instant fromTime;
    private Double fromLon;
    private Double fromLat;
    private Instant toTime;
    private double toLon;
    private double toLat;
    private double meters;
    private Instant createdAt;

    public TeamDistanceSegment() {
        this.createdAt = Instant.now();
    }

    /** True for the chain-start row: no from-fix (and, on a carryover, pre-migration meters). */
    public boolean isSeed() {
        return fromTime == null;
    }
}
