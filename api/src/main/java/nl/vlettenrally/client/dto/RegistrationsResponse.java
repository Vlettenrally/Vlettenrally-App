package nl.vlettenrally.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Response wrapper for bookings from the pre-registration API.
 */
@RegisterForReflection
@NoArgsConstructor
@Getter
@Setter
public class RegistrationsResponse {
    @JsonProperty("success")
    private boolean success;

    @JsonProperty("bookings")
    private List<Registration> registrations;

    @JsonProperty("total_bookings")
    private int totalBookings;

    @JsonProperty("total_attendees")
    private int totalAttendees;
}
