package nl.vlettenrally.client.dto.legendstracking;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@RegisterForReflection
@NoArgsConstructor
@Getter
@Setter
public class EventDetail {
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
}
