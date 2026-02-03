package nl.vlettenrally.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Details about a Socialrun event.
 */
@RegisterForReflection
@NoArgsConstructor
@Getter
@Setter
public class SocialrunDetail {
    @JsonProperty("id")
    private int id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("start_date")
    private String startDate;

    @JsonProperty("actual_start_date")
    private String actualStartDate;

    @JsonProperty("end_date")
    private String endDate;

    @JsonProperty("center")
    private Coordinates center;

    @JsonProperty("zoom")
    private int zoom;

    @JsonProperty("event_distance_category_id")
    private int eventDistanceCategoryId;
}
