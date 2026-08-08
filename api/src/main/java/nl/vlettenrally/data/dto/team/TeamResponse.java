package nl.vlettenrally.data.dto.team;

import java.util.List;
import java.util.UUID;

import io.quarkus.runtime.annotations.RegisterForReflection;
import nl.vlettenrally.data.enums.CheckInMethod;

@RegisterForReflection
public record TeamResponse(
    UUID id,
    UUID gameId,
    String name,
    String description,
    List<String> participants,
    UUID deviceId,
    boolean checkedIn,
    boolean checkedInDigitally,
    CheckInMethod checkInMethod,
    boolean finished,
    /* ISO-8601 instant the team was marked finished; null when it has not finished. */
    String finishedAt,
    boolean freeClass,
    boolean disqualified,
    /* ISO-8601 instant the team was marked disqualified; null when it has not been. */
    String disqualifiedAt,
    boolean traccarEnabled,
    String traccarIdentifier,
    String trackerId,
    boolean trackerHandedIn,
    /* ISO-8601 instant the tracker was marked handed in; null when it has not been. */
    String trackerHandedInAt,
    int totalPoints,
    List<UUID> rivals,
    String notes,
    List<String> emergencyPhoneNumbers,
    String createdAt,
    String updatedAt
) {}
