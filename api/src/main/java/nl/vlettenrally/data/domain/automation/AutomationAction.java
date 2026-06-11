package nl.vlettenrally.data.domain.automation;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import nl.vlettenrally.data.enums.AutomationActionType;
import nl.vlettenrally.data.enums.MessageType;

/**
 * One action of an automation rule. Flat record; which fields are required
 * depends on {@code type} (enforced by AutomationService validation):
 *
 * <ul>
 *   <li>SEND_MESSAGE — {@code messageContent} (+ optional messageType/messageIcon)</li>
 *   <li>AWARD_POINTS — {@code points} (+ optional pointsDescription)</li>
 *   <li>NOTIFY_ADMINS — {@code adminNotification}</li>
 * </ul>
 *
 * Text fields support a {@code {team}} placeholder replaced with the team name.
 */
@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AutomationAction(
    AutomationActionType type,
    String messageContent,
    MessageType messageType,
    String messageIcon,
    Integer points,
    String pointsDescription,
    String adminNotification
) {
}
