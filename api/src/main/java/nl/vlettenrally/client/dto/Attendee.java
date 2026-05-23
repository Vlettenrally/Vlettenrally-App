package nl.vlettenrally.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single boat entry within a booking from the pre-registration API.
 */
@RegisterForReflection
@NoArgsConstructor
@Getter
@Setter
public class Attendee {
    @JsonProperty("ticket_booking_id")
    private int ticketBookingId;

    @JsonProperty("bootnummer")
    private String boatNumber;

    @JsonProperty("aantal_deelnemers")
    private String participantCount;

    @JsonProperty("bijzonderheden")
    private String notes;
}
