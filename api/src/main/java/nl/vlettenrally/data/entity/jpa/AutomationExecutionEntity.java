package nl.vlettenrally.data.entity.jpa;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import nl.vlettenrally.data.enums.AutomationExecutionStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for one firing of an automation rule (audit log + dedup state).
 */
@Setter
@Getter
@Entity
@Table(name = "automation_executions", indexes = {
    @Index(name = "idx_automation_executions_rule_fired", columnList = "rule_id, fired_at"),
    @Index(name = "idx_automation_executions_dedup", columnList = "rule_id, team_id, context_key")
})
public class AutomationExecutionEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id", nullable = false)
    private AutomationRuleEntity rule;

    @Column(name = "game_id", nullable = false)
    private UUID gameId;

    @Column(name = "team_id")
    private UUID teamId;

    @Column(name = "context_key", length = 100)
    private String contextKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AutomationExecutionStatus status;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    @Column(name = "fired_at", nullable = false, updatable = false)
    private Instant firedAt;

    public AutomationExecutionEntity() {
        this.id = UUID.randomUUID();
        this.firedAt = Instant.now();
    }
}
