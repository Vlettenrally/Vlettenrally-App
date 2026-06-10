package nl.vlettenrally.client.dto.legendstracking;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@RegisterForReflection
@NoArgsConstructor
@Getter
@Setter
public class EventParticipantCheckpoint {
    @JsonProperty("event_participants_id")
    private int eventParticipantsId;

    @JsonProperty("tracker_id")
    private Integer trackerId;

    @JsonProperty("checkpoint_id")
    private int checkpointId;

    @JsonProperty("location_time")
    private String locationTime;

    @JsonProperty("checkpoint_sequence")
    private Integer checkpointSequence;

    @JsonProperty("time_elapsed")
    private String timeElapsed;
}
