package nl.vlettenrally.data.mapper;

import nl.vlettenrally.data.domain.AutomationExecution;
import nl.vlettenrally.data.entity.jpa.AutomationExecutionEntity;

/**
 * Mapper between AutomationExecution domain and AutomationExecutionEntity (JPA).
 */
public final class AutomationExecutionMapper {

    private AutomationExecutionMapper() {}

    public static AutomationExecution toDomain(AutomationExecutionEntity entity) {
        if (entity == null) return null;

        AutomationExecution domain = new AutomationExecution();
        domain.setId(entity.getId());
        domain.setRuleId(entity.getRule() != null ? entity.getRule().getId() : null);
        domain.setGameId(entity.getGameId());
        domain.setTeamId(entity.getTeamId());
        domain.setContextKey(entity.getContextKey());
        domain.setStatus(entity.getStatus());
        domain.setDetail(entity.getDetail());
        domain.setFiredAt(entity.getFiredAt());
        return domain;
    }

    /**
     * Convert domain to a new entity. The Rule relationship must be set separately.
     */
    public static AutomationExecutionEntity toEntity(AutomationExecution domain) {
        if (domain == null) return null;

        AutomationExecutionEntity entity = new AutomationExecutionEntity();
        if (domain.getId() != null) {
            entity.setId(domain.getId());
        }
        entity.setGameId(domain.getGameId());
        entity.setTeamId(domain.getTeamId());
        entity.setContextKey(domain.getContextKey());
        entity.setStatus(domain.getStatus());
        entity.setDetail(domain.getDetail());
        if (domain.getFiredAt() != null) {
            entity.setFiredAt(domain.getFiredAt());
        }
        return entity;
    }
}
