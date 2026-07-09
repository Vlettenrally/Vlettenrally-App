package nl.vlettenrally.client.dto.legendstracking;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Body of {@link EventParticipantTrackResponse}. The {@code participantTrack} array holds the
 * historical fixes; each point has the same shape as a live {@link EventParticipantLocation}
 * (it additionally carries {@code is_buffered}, which the mapper ignores). {@code participantDetails}
 * is not consumed, so it is intentionally left unmapped (unknown properties are ignored).
 */
@RegisterForReflection
@NoArgsConstructor
@Getter
@Setter
public class ParticipantTrackBody {
    @JsonProperty("participantTrack")
    private List<EventParticipantLocation> participantTrack;
}
