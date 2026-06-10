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
public class EventParticipantDistance {
    @JsonProperty("tracker_id")
    private Integer trackerId;

    @JsonProperty("event_participant_id")
    private Integer eventParticipantId;

    @JsonProperty("location_time")
    private String locationTime;

    @JsonProperty("distance_off_course")
    private Double distanceOffCourse;

    @JsonProperty("distance_on_gpx")
    private Double distanceOnGpx;

    @JsonProperty("average_speed")
    private Double averageSpeed;

    @JsonProperty("next_checkpoint_id")
    private Integer nextCheckpointId;

    @JsonProperty("next_checkpoint_eta")
    private String nextCheckpointEta;
}
