package nl.vlettenrally.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Response wrapper for events from the pre-registration API.
 */
@RegisterForReflection
@NoArgsConstructor
@Getter
@Setter
public class EventsResponse {
    @JsonProperty("events")
    private List<ExternalEvent> events;
}
