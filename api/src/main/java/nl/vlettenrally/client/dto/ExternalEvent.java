package nl.vlettenrally.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * External event from the pre-registration API.
 */
@RegisterForReflection
@NoArgsConstructor
@Getter
@Setter
public class ExternalEvent {
    @JsonProperty("event_id")
    private int eventId;

    @JsonProperty("post_id")
    private int postId;

    @JsonProperty("event_name")
    private String eventName;

    @JsonProperty("booking_count")
    private int bookingCount;
}
