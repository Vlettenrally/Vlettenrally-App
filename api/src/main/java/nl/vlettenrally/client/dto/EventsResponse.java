package nl.vlettenrally.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * Response wrapper for events from the pre-registration API.
 */
@RegisterForReflection
public class EventsResponse {
    @JsonProperty("events")
    private List<ExternalEvent> events;

    public List<ExternalEvent> getEvents() {
        return events;
    }

    public void setEvents(List<ExternalEvent> events) {
        this.events = events;
    }
}
