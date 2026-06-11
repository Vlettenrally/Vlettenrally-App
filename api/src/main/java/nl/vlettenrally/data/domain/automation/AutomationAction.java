package nl.vlettenrally.data.domain.automation;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import nl.vlettenrally.data.enums.AutomationActionType;
import nl.vlettenrally.data.enums.MessageType;

import java.util.UUID;

/**
 * One action of an automation rule. Flat record; which fields are required
 * depends on {@code type} (enforced by AutomationService validation):
 *
 * <ul>
 *   <li>SEND_MESSAGE — either {@code messageTemplateId} (live reference to a
 *       reusable MessageTemplate, providing content/type/icon) or
 *       {@code messageContent} (+ optional messageType/messageIcon)</li>
 *   <li>AWARD_POINTS — {@code points} (+ optional pointsDescription)</li>
 *   <li>NOTIFY_ADMINS — {@code adminNotification}</li>
 * </ul>
 *
 * Text fields support {@code {team}}, {@code {checkpoint}} and {@code {rival}}
 * placeholders, resolved against the firing context (which apply depends on the
 * rule's trigger type).
 */
@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AutomationAction(
    AutomationActionType type,
    String messageContent,
    MessageType messageType,
    String messageIcon,
    UUID messageTemplateId,
    Integer points,
    String pointsDescription,
    String adminNotification
) {
}
