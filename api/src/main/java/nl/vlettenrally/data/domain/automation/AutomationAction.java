package nl.vlettenrally.data.domain.automation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.quarkus.runtime.annotations.RegisterForReflection;
import nl.vlettenrally.data.enums.MessageType;

import java.util.UUID;

/**
 * Polymorphic action in an automation rule. The {@code type} field is the JSON
 * discriminator; each subtype carries only the fields it needs.
 * <p>
 * The DB stores these as a JSON array; the string names in {@link JsonSubTypes}
 * match the values previously stored by the flat-record format so existing rows
 * deserialise without migration.
 * <p>
 * To add a new action type: add a subtype record here, register it in
 * {@link JsonSubTypes}, add a case to {@link nl.vlettenrally.service.AutomationActionExecutor}
 * and {@link nl.vlettenrally.service.AutomationService#validateActions}.
 */
@RegisterForReflection(targets = {
    AutomationAction.SendMessage.class,
    AutomationAction.AwardPoints.class,
    AutomationAction.NotifyAdmins.class,
    AutomationAction.FinishTeam.class,
    AutomationAction.SetRankingsPublished.class,
    AutomationAction.SetPublicRankingsEnabled.class,
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = AutomationAction.SendMessage.class,            name = "SEND_MESSAGE"),
    @JsonSubTypes.Type(value = AutomationAction.AwardPoints.class,            name = "AWARD_POINTS"),
    @JsonSubTypes.Type(value = AutomationAction.NotifyAdmins.class,           name = "NOTIFY_ADMINS"),
    @JsonSubTypes.Type(value = AutomationAction.FinishTeam.class,             name = "FINISH_TEAM"),
    @JsonSubTypes.Type(value = AutomationAction.SetRankingsPublished.class,   name = "SET_RANKINGS_PUBLISHED"),
    @JsonSubTypes.Type(value = AutomationAction.SetPublicRankingsEnabled.class, name = "SET_PUBLIC_RANKINGS_ENABLED"),
})
public sealed interface AutomationAction
    permits AutomationAction.SendMessage,
            AutomationAction.AwardPoints,
            AutomationAction.NotifyAdmins,
            AutomationAction.FinishTeam,
            AutomationAction.SetRankingsPublished,
            AutomationAction.SetPublicRankingsEnabled {

    /** Multi-channel message to the affected team(s). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SendMessage(
        String messageContent,
        MessageType messageType,
        String messageIcon,
        UUID messageTemplateId
    ) implements AutomationAction {}

    /** Ledger entry of type AUTOMATION_BONUS for the affected team(s). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AwardPoints(
        Integer points,
        String pointsDescription
    ) implements AutomationAction {}

    /** Admin-dashboard WebSocket notification. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record NotifyAdmins(
        String adminNotification
    ) implements AutomationAction {}

    /** Mark the affected team(s) finished: no further automatic points. */
    record FinishTeam() implements AutomationAction {}

    /** Set game.rankingsPublished — true = publish to players, false = hide. */
    record SetRankingsPublished(
        Boolean rankingsPublished
    ) implements AutomationAction {}

    /** Set game.publicRankingsEnabled — true = enable public API, false = disable. */
    record SetPublicRankingsEnabled(
        Boolean publicRankingsEnabled
    ) implements AutomationAction {}
}
