package nl.vlettenrally.data.dto.automation;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;
import java.util.UUID;

@RegisterForReflection
public record AutomationExecutionResponse(
    UUID id,
    UUID ruleId,
    UUID teamId,
    String teamName,
    String contextKey,
    String status,
    String detail,
    Instant firedAt
) {}
