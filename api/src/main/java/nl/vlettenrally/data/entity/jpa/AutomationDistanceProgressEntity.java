package nl.vlettenrally.data.entity.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for per (distance rule, team) accrual state. Composite key of
 * plain UUID columns — no object relations needed, the engine always looks
 * rows up by (ruleId, teamId).
 */
@Setter
@Getter
@Entity
@Table(name = "automation_distance_progress")
@IdClass(AutomationDistanceProgressEntity.Key.class)
public class AutomationDistanceProgressEntity {

    @Id
    @Column(name = "rule_id", nullable = false, updatable = false)
    private UUID ruleId;

    @Id
    @Column(name = "team_id", nullable = false, updatable = false)
    private UUID teamId;

    @Column(name = "last_reported_meters", nullable = false)
    private double lastReportedMeters;

    @Column(name = "leftover_meters", nullable = false)
    private double leftoverMeters;

    @Column(name = "credited_points", nullable = false)
    private int creditedPoints;

    @Column(name = "point_entry_id")
    private UUID pointEntryId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private UUID ruleId;
        private UUID teamId;
    }
}
