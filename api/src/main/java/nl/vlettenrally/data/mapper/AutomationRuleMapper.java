package nl.vlettenrally.data.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import nl.vlettenrally.data.domain.AutomationRule;
import nl.vlettenrally.data.domain.automation.AutomationAction;
import nl.vlettenrally.data.domain.automation.AutomationTriggerConfig;
import nl.vlettenrally.data.entity.jpa.AutomationRuleEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mapper between AutomationRule domain and AutomationRuleEntity (JPA).
 *
 * The trigger config, actions and target team ids live as JSON text on the
 * entity; all (de)serialization is concentrated here so neither the entity nor
 * the repositories touch JSON.
 */
public final class AutomationRuleMapper {

    private static final ObjectMapper JSON = new ObjectMapper()
        .findAndRegisterModules()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private static final TypeReference<List<AutomationAction>> ACTIONS_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<UUID>> UUID_LIST_TYPE = new TypeReference<>() {};

    private AutomationRuleMapper() {}

    public static AutomationRule toDomain(AutomationRuleEntity entity) {
        if (entity == null) return null;

        AutomationRule domain = new AutomationRule();
        domain.setId(entity.getId());
        domain.setGameId(entity.getGame() != null ? entity.getGame().getId() : null);
        domain.setName(entity.getName());
        domain.setEnabled(entity.isEnabled());
        domain.setSystem(entity.isSystem());
        domain.setTriggerType(entity.getTriggerType());
        domain.setTriggerConfig(readTriggerConfig(entity.getTriggerConfig()));
        domain.setActions(readActions(entity.getActions()));
        domain.setTargetTeamIds(readUuidList(entity.getTargetTeamIds()));
        domain.setFireOnce(entity.isFireOnce());
        domain.setCooldownMinutes(entity.getCooldownMinutes());
        domain.setCreatedBy(entity.getCreatedBy());
        domain.setCreatedAt(entity.getCreatedAt());
        domain.setUpdatedAt(entity.getUpdatedAt());
        return domain;
    }

    /**
     * Convert domain to a new entity. The Game relationship must be set separately.
     */
    public static AutomationRuleEntity toEntity(AutomationRule domain) {
        if (domain == null) return null;

        AutomationRuleEntity entity = new AutomationRuleEntity();
        if (domain.getId() != null) {
            entity.setId(domain.getId());
        }
        if (domain.getCreatedAt() != null) {
            entity.setCreatedAt(domain.getCreatedAt());
        }
        entity.setCreatedBy(domain.getCreatedBy());
        entity.setSystem(domain.isSystem());
        entity.setTriggerType(domain.getTriggerType());
        updateEntity(entity, domain);
        return entity;
    }

    /** Apply the mutable fields of the domain rule onto an existing entity. */
    public static void updateEntity(AutomationRuleEntity entity, AutomationRule domain) {
        if (entity == null || domain == null) return;

        entity.setName(domain.getName());
        entity.setEnabled(domain.isEnabled());
        entity.setTriggerType(domain.getTriggerType());
        entity.setTriggerConfig(writeJson(domain.getTriggerConfig()));
        entity.setActions(writeActions(domain.getActions() != null ? domain.getActions() : List.of()));
        entity.setTargetTeamIds(domain.getTargetTeamIds() == null || domain.getTargetTeamIds().isEmpty()
            ? null : writeJson(domain.getTargetTeamIds()));
        entity.setFireOnce(domain.isFireOnce());
        entity.setCooldownMinutes(domain.getCooldownMinutes());
        entity.setUpdatedAt(domain.getUpdatedAt());
    }

    private static AutomationTriggerConfig readTriggerConfig(String json) {
        if (json == null || json.isBlank()) {
            return new AutomationTriggerConfig(null, null, null, null, null, null, null, null, null);
        }
        try {
            return JSON.readValue(json, AutomationTriggerConfig.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt automation trigger_config: " + json, e);
        }
    }

    private static List<AutomationAction> readActions(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return JSON.readValue(json, ACTIONS_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt automation actions: " + json, e);
        }
    }

    private static List<UUID> readUuidList(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return JSON.readValue(json, UUID_LIST_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt automation target_team_ids: " + json, e);
        }
    }

    private static String writeActions(List<AutomationAction> actions) {
        // Use the typed writer so Jackson resolves the element type as AutomationAction
        // and applies @JsonTypeInfo. writeValueAsString(Object) erases the generic type,
        // which causes the type discriminator to be silently dropped in native image.
        try {
            return JSON.writerFor(ACTIONS_TYPE).writeValueAsString(actions);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize automation actions JSON", e);
        }
    }

    private static String writeJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize automation JSON", e);
        }
    }
}
