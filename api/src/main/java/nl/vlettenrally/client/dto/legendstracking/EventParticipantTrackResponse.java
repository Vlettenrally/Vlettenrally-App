package nl.vlettenrally.client.dto.legendstracking;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response of the Legendstracking {@code /api/event/{eventParticipantId}/participant_track}
 * endpoint, which returns a single participant's <em>full</em> historical track (used for
 * backfilling gaps), unlike {@code /participants_locations} which only returns the latest fix.
 */
@RegisterForReflection
@NoArgsConstructor
@Getter
@Setter
public class EventParticipantTrackResponse {
    @JsonProperty("participantTrack")
    private ParticipantTrackBody participantTrack;
}
