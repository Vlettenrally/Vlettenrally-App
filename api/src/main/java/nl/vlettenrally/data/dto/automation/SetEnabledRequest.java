package nl.vlettenrally.data.dto.automation;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record SetEnabledRequest(boolean enabled) {}
