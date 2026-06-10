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
public class EventParticipantsCheckpointsResponse {
    @JsonProperty("event")
    private EventParticipantCheckpointsBody event;
}
