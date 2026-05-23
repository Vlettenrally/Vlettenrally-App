package nl.vlettenrally.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Booking from the pre-registration API. Each booking represents one
 * scouting group's registration and contains one or more boat attendees.
 */
@RegisterForReflection
@NoArgsConstructor
@Getter
@Setter
public class Registration {
    @JsonProperty("booking_id")
    private int bookingId;

    @JsonProperty("event_id")
    private int eventId;

    @JsonProperty("event_name")
    private String eventName;

    @JsonProperty("booking_date")
    private String bookingDate;

    @JsonProperty("booking_status")
    private int bookingStatus;

    @JsonProperty("naam_scoutinggroep")
    private String scoutingGroup;

    @JsonProperty("contactpersoon")
    private String contactPerson;

    @JsonProperty("contactpersoon_email")
    private String contactEmail;

    @JsonProperty("telefoonnummer_1")
    private String phoneNumber1;

    @JsonProperty("telefoonnummer_2")
    private String phoneNumber2;

    @JsonProperty("attendees")
    private List<Attendee> attendees;

    @JsonProperty("aantal_boten")
    private int boatCount;
}
