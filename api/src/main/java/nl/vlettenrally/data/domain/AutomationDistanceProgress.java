package nl.vlettenrally.data.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Per (distance rule, team) accrual state for DISTANCE_TRAVELED rules.
 *
 * {@code lastReportedMeters} is the provider's cumulative counter at the last
 * poll — kept up to date even while the rule is disabled, so meters traveled
 * during a disabled period are never credited retroactively on re-enable.
 * {@code leftoverMeters} carries the creditable remainder below one whole
 * point, so a mid-game rate change only affects future meters.
 */
@Getter
@Setter
public class AutomationDistanceProgress {
    private UUID ruleId;
    private UUID teamId;
    private double lastReportedMeters;
    private double leftoverMeters;
    private int creditedPoints;
    private UUID pointEntryId;      // nullable until the first point is credited
    private Instant updatedAt;

    public AutomationDistanceProgress() {
        this.updatedAt = Instant.now();
    }
}
