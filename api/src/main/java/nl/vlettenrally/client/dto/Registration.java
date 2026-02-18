package nl.vlettenrally.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Team registration from the pre-registration API.
 */
@RegisterForReflection
@NoArgsConstructor
@Getter
@Setter
public class Registration {
    @JsonProperty("em_booking_id")
    private int bookingId;

    @JsonProperty("em_event_id")
    private int eventId;

    @JsonProperty("event_name")
    private String eventName;

    @JsonProperty("boat_number")
    private String teamName;

    @JsonProperty("scouting_group")
    private String scoutingGroup;

    @JsonProperty("booking_date")
    private String bookingDate;
}
