package nl.vlettenrally.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Location data for a participant from the tracking API.
 */
@RegisterForReflection
@NoArgsConstructor
@Getter
@Setter
public class ParticipantLocation {
    @JsonProperty("event_participants_id")
    private int eventParticipantsId;

    @JsonProperty("event_id")
    private int eventId;

    @JsonProperty("status_id")
    private int statusId;

    @JsonProperty("bib")
    private int bib;

    @JsonProperty("bib_label")
    private String bibLabel;

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("last_name")
    private String lastName;

    @JsonProperty("country")
    private String country;

    @JsonProperty("gender")
    private String gender;

    @JsonProperty("tracker_id")
    private int trackerId;

    @JsonProperty("location_time")
    private String locationTime;

    @JsonProperty("report_time")
    private String reportTime;

    @JsonProperty("location")
    private Coordinates location;

    @JsonProperty("battery_percentage")
    private int batteryPercentage;

    @JsonProperty("gps_accuracy")
    private int gpsAccuracy;

    @JsonProperty("is_sos_report")
    private int isSosReport;
}
