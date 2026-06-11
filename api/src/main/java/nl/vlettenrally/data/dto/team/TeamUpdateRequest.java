package nl.vlettenrally.data.dto.team;

import java.util.List;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record TeamUpdateRequest(
    String name,
    String description,
    List<String> participants,
    List<String> emergencyPhoneNumbers,
    Boolean checkedIn,
    Boolean checkedInDigitally,
    Boolean traccarEnabled,
    String notes
) {}
