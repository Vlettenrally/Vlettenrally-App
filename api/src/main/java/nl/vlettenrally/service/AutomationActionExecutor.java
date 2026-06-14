package nl.vlettenrally.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.AdminNotification;
import nl.vlettenrally.data.domain.AutomationRule;
import nl.vlettenrally.data.domain.MessageTemplate;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.domain.automation.AutomationAction;
import nl.vlettenrally.data.enums.AutomationExecutionStatus;
import nl.vlettenrally.data.enums.MessageType;
import nl.vlettenrally.data.enums.PointEntryType;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.websockets.WebSocketPublisher;
import nl.vlettenrally.websockets.dto.WebSocketMessageType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    @Inject
    MessageTemplateService messageTemplateService;

    @Inject
    AdminInboxService adminInboxService;

    @Inject
    TeamRepository teamRepository;

    /** Outcome of one rule firing: overall status plus per-action detail as JSON. */
    public record Result(AutomationExecutionStatus status, String detail) {}

    /**
     * Firing-context names available to placeholders. {@code checkpointName} is
     * set for CHECKPOINT_ENTRY firings, {@code rivalName} for RIVAL_OVERTAKE;
     * {@code team} is resolved per recipient downstream (or for single-team
     * admin notifications here).
     */
    public record ActionContext(String checkpointName, String rivalName) {
        public static final ActionContext EMPTY = new ActionContext(null, null);
    }

    public Result execute(AutomationRule rule, List<Team> targets, ActionContext ctx) {
        List<Map<String, Object>> details = new ArrayList<>();
        int failed = 0;

        for (AutomationAction action : rule.getActions()) {
            Map<String, Object> detail = new HashMap<>();
            detail.put("action", action.type().name());
            try {
                switch (action.type()) {
                    case SEND_MESSAGE -> sendMessage(rule, action, targets, ctx);
                    case AWARD_POINTS -> awardPoints(rule, action, targets, detail);
                    case NOTIFY_ADMINS -> notifyAdmins(rule, action, targets, ctx);
                    case FINISH_TEAM -> finishTeams(targets, detail);
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

    private void sendMessage(AutomationRule rule, AutomationAction action, List<Team> targets, ActionContext ctx) {
        String content = action.messageContent();
        MessageType type = action.messageType();
        String icon = action.messageIcon();

        // A template id is a live reference: use the template's content/type/icon,
        // falling back to the action's own fields if it has been deleted.
        if (action.messageTemplateId() != null) {
            Optional<MessageTemplate> template = messageTemplateService.findById(action.messageTemplateId());
            if (template.isPresent()) {
                content = template.get().getContent();
                type = template.get().getMessageType();
                icon = template.get().getIcon();
            }
        }

        if (content == null || content.isBlank()) {
            throw new IllegalStateException("No message content (template "
                + action.messageTemplateId() + " missing and no fallback text)");
        }

        // Resolve firing-context placeholders now; {team} is resolved per
        // recipient by MessageService, so one batch covers all targets.
        String resolved = MessagePlaceholders.resolve(content, firingValues(ctx));
        messageService.sendAutomationMessage(
            rule.getGameId(),
            targets.stream().map(Team::getId).toList(),
            type != null ? type : MessageType.GENERAL,
            resolved,
            icon,
            rule.getId());
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

    /**
     * Mark each target team finished: it keeps its points and ranking but stops
     * accruing automatic points (checkpoint passes + distance) — the gate is read
     * by {@code LocationIngestService} on the next fix. Idempotent: an
     * already-finished team is left untouched.
     */
    private void finishTeams(List<Team> targets, Map<String, Object> detail) {
        List<String> teamErrors = new ArrayList<>();
        for (Team target : targets) {
            try {
                Team team = teamRepository.findById(target.getId()).orElse(target);
                if (team.isFinished()) {
                    continue;
                }
                team.setFinished(true);
                team.setFinishedAt(Instant.now());
                teamRepository.save(team);
            } catch (Exception e) {
                teamErrors.add(target.getName() + ": " + e.getMessage());
            }
        }
        if (!teamErrors.isEmpty()) {
            detail.put("ok", teamErrors.size() < targets.size());
            detail.put("error", String.join("; ", teamErrors));
        }
    }

    private void notifyAdmins(AutomationRule rule, AutomationAction action, List<Team> targets, ActionContext ctx) {
        // One notification per firing; {team} only resolves for single-team firings.
        Team single = targets.size() == 1 ? targets.getFirst() : null;
        Map<String, String> values = firingValues(ctx);
        if (single != null) {
            values.put("team", single.getName());
        }
        String text = String.valueOf(MessagePlaceholders.resolve(action.adminNotification(), values));
        String metadataJson = toSingleObjectJson(Map.of(
            "ruleId", rule.getId(),
            "ruleName", rule.getName(),
            "teamId", single != null ? single.getId().toString() : "",
            "teamName", single != null ? single.getName() : ""
        ));
        AdminNotification notification = adminInboxService.createNotification(
            rule.getGameId(),
            single != null ? single.getId() : null,
            WebSocketMessageType.AUTOMATION_NOTIFICATION.getValue(),
            text,
            metadataJson
        );

        webSocketPublisher.sendToAdmins(
            rule.getGameId(),
            WebSocketMessageType.AUTOMATION_NOTIFICATION.getValue(),
            Map.of(
                "notificationId", notification.getId(),
                "ruleId", rule.getId(),
                "ruleName", rule.getName(),
                "teamId", single != null ? single.getId().toString() : "",
                "teamName", single != null ? single.getName() : "",
                "text", text,
                "source", WebSocketMessageType.AUTOMATION_NOTIFICATION.getValue(),
                "createdAt", notification.getCreatedAt().toString(),
                "read", notification.isRead()
            ));
    }

    /** Mutable map of the firing-context placeholder values ({checkpoint}, {rival}). */
    private static Map<String, String> firingValues(ActionContext ctx) {
        Map<String, String> values = new LinkedHashMap<>();
        if (ctx != null) {
            values.put("checkpoint", ctx.checkpointName());
            values.put("rival", ctx.rivalName());
        }
        return values;
    }

    private static String toJson(List<Map<String, Object>> details) {
        try {
            return JSON.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private static String toSingleObjectJson(Map<String, Object> payload) {
        try {
            return JSON.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
