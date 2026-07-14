package nl.vlettenrally.data.dto.team;

import java.util.List;

import io.quarkus.runtime.annotations.RegisterForReflection;
import nl.vlettenrally.data.enums.CheckInMethod;

@RegisterForReflection
public record TeamUpdateRequest(
    String name,
    String description,
    List<String> participants,
    List<String> emergencyPhoneNumbers,
    Boolean checkedIn,
    Boolean checkedInDigitally,
    CheckInMethod checkInMethod,
    Boolean traccarEnabled,
    Boolean finished,
    String notes
) {}
