package nl.vlettenrally.data.dto.team;

import java.util.List;
import java.util.UUID;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record TeamCreateRequest(
    UUID gameId,
    String name,
    String description,
    List<String> participants,
    List<String> emergencyPhoneNumbers,
    String notes
) {}
