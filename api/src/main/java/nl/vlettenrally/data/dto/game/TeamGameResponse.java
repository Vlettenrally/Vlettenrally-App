package nl.vlettenrally.data.dto.game;

import java.util.UUID;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record TeamGameResponse(
    UUID id,
    String name,
    String description,
    String participantInfo,
    String startTime,
    String endTime,
    String checkInStartTime,
    String status,
    Boolean rankingsPublished,
    Boolean publicRankingsEnabled,
    /** Why the rally was stopped. Only set while the game is paused. */
    String pauseReason
) {}
