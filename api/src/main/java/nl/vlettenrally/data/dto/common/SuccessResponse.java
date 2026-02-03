package nl.vlettenrally.data.dto.common;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record SuccessResponse(boolean success) {}
