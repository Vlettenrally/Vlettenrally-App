package nl.vlettenrally.data.mapper;

import nl.vlettenrally.data.domain.AutomationDistanceProgress;
import nl.vlettenrally.data.entity.jpa.AutomationDistanceProgressEntity;

/**
 * Mapper between AutomationDistanceProgress domain and its JPA entity.
 */
public final class AutomationDistanceProgressMapper {

    private AutomationDistanceProgressMapper() {}

    public static AutomationDistanceProgress toDomain(AutomationDistanceProgressEntity entity) {
        if (entity == null) return null;

        AutomationDistanceProgress domain = new AutomationDistanceProgress();
        domain.setRuleId(entity.getRuleId());
        domain.setTeamId(entity.getTeamId());
        domain.setLastReportedMeters(entity.getLastReportedMeters());
        domain.setLeftoverMeters(entity.getLeftoverMeters());
        domain.setCreditedPoints(entity.getCreditedPoints());
        domain.setPointEntryId(entity.getPointEntryId());
        domain.setUpdatedAt(entity.getUpdatedAt());
        return domain;
    }

    public static AutomationDistanceProgressEntity toEntity(AutomationDistanceProgress domain) {
        if (domain == null) return null;

        AutomationDistanceProgressEntity entity = new AutomationDistanceProgressEntity();
        entity.setRuleId(domain.getRuleId());
        entity.setTeamId(domain.getTeamId());
        updateEntity(entity, domain);
        return entity;
    }

    public static void updateEntity(AutomationDistanceProgressEntity entity, AutomationDistanceProgress domain) {
        if (entity == null || domain == null) return;

        entity.setLastReportedMeters(domain.getLastReportedMeters());
        entity.setLeftoverMeters(domain.getLeftoverMeters());
        entity.setCreditedPoints(domain.getCreditedPoints());
        entity.setPointEntryId(domain.getPointEntryId());
        entity.setUpdatedAt(domain.getUpdatedAt());
    }
}
