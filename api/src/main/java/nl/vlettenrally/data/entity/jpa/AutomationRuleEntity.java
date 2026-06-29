package nl.vlettenrally.data.entity.jpa;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import nl.vlettenrally.data.enums.AutomationTriggerType;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for an automation rule. The trigger config, actions and target
 * team ids are stored as JSON text; (de)serialization happens in
 * {@link nl.vlettenrally.data.mapper.AutomationRuleMapper} so this entity
 * stays a dumb string holder.
 */
@Setter
@Getter
@Entity
@Table(name = "automation_rules", indexes = {
    @Index(name = "idx_automation_rules_game", columnList = "game_id")
})
public class AutomationRuleEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private GameEntity game;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "system", nullable = false)
    private boolean system;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 40)
    private AutomationTriggerType triggerType;

    @Column(name = "trigger_config", nullable = false, columnDefinition = "TEXT")
    private String triggerConfig;

    @Column(name = "actions", nullable = false, columnDefinition = "TEXT")
    private String actions;

    @Column(name = "target_team_ids", columnDefinition = "TEXT")
    private String targetTeamIds;

    @Column(name = "fire_once", nullable = false)
    private boolean fireOnce = true;

    @Column(name = "cooldown_minutes")
    private Integer cooldownMinutes;

    @Column(name = "trigger_for_finished_teams", nullable = false)
    private boolean triggerForFinishedTeams = false;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public AutomationRuleEntity() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }
}
