package nl.vlettenrally.data.dto.message;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record UnreadCountResponse(int count) {}
