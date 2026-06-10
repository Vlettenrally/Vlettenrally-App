package nl.vlettenrally.client.dto.legendstracking;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.vlettenrally.client.dto.Coordinates;

@RegisterForReflection
@NoArgsConstructor
@Getter
@Setter
public class EventParticipantLocation {
    @JsonProperty("event_participants_id")
    private int eventParticipantsId;

    @JsonProperty("status_id")
    private Integer statusId;

    @JsonProperty("tracker_id")
    private Integer trackerId;

    @JsonProperty("location_time")
    private String locationTime;

    @JsonProperty("report_time")
    private String reportTime;

    @JsonProperty("location")
    private Coordinates location;

    @JsonProperty("battery_percentage")
    private Integer batteryPercentage;

    @JsonProperty("is_sos_report")
    private Integer isSosReport;

    @JsonProperty("gps_accuracy")
    private Integer gpsAccuracy;

    @JsonProperty("speed")
    private Double speed;
}
