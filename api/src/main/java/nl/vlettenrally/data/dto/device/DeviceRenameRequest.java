package nl.vlettenrally.data.dto.device;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record DeviceRenameRequest(String nickname) {}
