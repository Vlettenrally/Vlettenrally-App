package nl.vlettenrally.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.AutomationRule;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.domain.automation.AutomationAction;
import nl.vlettenrally.data.enums.AutomationExecutionStatus;
import nl.vlettenrally.data.enums.MessageType;
import nl.vlettenrally.data.enums.PointEntryType;
import nl.vlettenrally.websockets.WebSocketPublisher;
import nl.vlettenrally.websockets.dto.WebSocketMessageType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes the action list of a fired automation rule against the resolved
 * target teams. Failures of individual actions (or individual teams within an
 * action) are collected, never thrown — the engine records them in the
 * execution log.
 */
@ApplicationScoped
public class AutomationActionExecutor {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Inject
    MessageService messageService;

    @Inject
    PointsLedgerService pointsLedgerService;

    @Inject
    WebSocketPublisher webSocketPublisher;

    /** Outcome of one rule firing: overall status plus per-action detail as JSON. */
    public record Result(AutomationExecutionStatus status, String detail) {}

    public Result execute(AutomationRule rule, List<Team> targets) {
        List<Map<String, Object>> details = new ArrayList<>();
        int failed = 0;

        for (AutomationAction action : rule.getActions()) {
            Map<String, Object> detail = new HashMap<>();
            detail.put("action", action.type().name());
            try {
                switch (action.type()) {
                    case SEND_MESSAGE -> sendMessage(rule, action, targets);
                    case AWARD_POINTS -> awardPoints(rule, action, targets, detail);
                    case NOTIFY_ADMINS -> notifyAdmins(rule, action, targets);
                }
                detail.put("ok", !Boolean.FALSE.equals(detail.get("ok")));
            } catch (Exception e) {
                Log.warnf("Automation %s action %s failed: %s", rule.getId(), action.type(), e.getMessage());
                detail.put("ok", false);
                detail.put("error", e.getMessage());
            }
            if (Boolean.FALSE.equals(detail.get("ok"))) {
                failed++;
            }
            details.add(detail);
        }

        AutomationExecutionStatus status = failed == 0 ? AutomationExecutionStatus.SUCCESS
            : failed == rule.getActions().size() ? AutomationExecutionStatus.FAILED
            : AutomationExecutionStatus.PARTIAL;
        return new Result(status, toJson(details));
    }

    private void sendMessage(AutomationRule rule, AutomationAction action, List<Team> targets) {
        MessageType type = action.messageType() != null ? action.messageType() : MessageType.GENERAL;
        // Per-team sends so the {team} placeholder can be substituted per recipient.
        for (Team team : targets) {
            messageService.sendAutomationMessage(
                rule.getGameId(),
                List.of(team.getId()),
                type,
                substitute(action.messageContent(), team),
                action.messageIcon(),
                rule.getId());
        }
    }

    private void awardPoints(AutomationRule rule, AutomationAction action, List<Team> targets,
                             Map<String, Object> detail) {
        String description = action.pointsDescription() != null
            ? action.pointsDescription()
            : "Automation: " + rule.getName();
        List<String> teamErrors = new ArrayList<>();
        for (Team team : targets) {
            try {
                pointsLedgerService.createEntry(team.getId(), rule.getGameId(),
                    PointEntryType.AUTOMATION_BONUS, action.points(), null, description, null, null);
            } catch (Exception e) {
                teamErrors.add(team.getName() + ": " + e.getMessage());
            }
        }
        if (!teamErrors.isEmpty()) {
            detail.put("ok", teamErrors.size() < targets.size());
            detail.put("error", String.join("; ", teamErrors));
        }
    }

    private void notifyAdmins(AutomationRule rule, AutomationAction action, List<Team> targets) {
        // One notification per firing; {team} only resolves for single-team firings.
        Team single = targets.size() == 1 ? targets.get(0) : null;
        webSocketPublisher.sendToAdmins(
            rule.getGameId(),
            WebSocketMessageType.AUTOMATION_NOTIFICATION.getValue(),
            Map.of(
                "ruleId", rule.getId(),
                "ruleName", rule.getName(),
                "teamId", single != null ? single.getId().toString() : "",
                "teamName", single != null ? single.getName() : "",
                "text", single != null ? substitute(action.adminNotification(), single)
                                       : String.valueOf(action.adminNotification())
            ));
    }

    private static String substitute(String text, Team team) {
        return text == null ? null : text.replace("{team}", team.getName());
    }

    private static String toJson(List<Map<String, Object>> details) {
        try {
            return JSON.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
