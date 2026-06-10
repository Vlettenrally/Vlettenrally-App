package nl.vlettenrally.client.dto.legendstracking;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@RegisterForReflection
@NoArgsConstructor
@Getter
@Setter
public class EventDetailsBody {
    @JsonProperty("eventDetails")
    private List<EventDetail> eventDetails;

    @JsonProperty("eventParticipants")
    private List<EventParticipant> eventParticipants;

    @JsonProperty("eventCheckpoints")
    private List<EventCheckpoint> eventCheckpoints;
}
