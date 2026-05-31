package nl.vlettenrally.data.dto.auth;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;
import java.util.UUID;

@RegisterForReflection
public record TodayGameResponse(
    UUID id,
    String name,
    String description,
    Instant startTime
) {}
