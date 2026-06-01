package nl.vlettenrally.data.dto.team;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;
import java.util.UUID;

@RegisterForReflection
public record RivalsUpdateRequest(List<UUID> rivalTeamIds) {}
