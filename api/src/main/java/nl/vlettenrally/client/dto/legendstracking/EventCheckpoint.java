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
public class EventCheckpoint {
    @JsonProperty("id")
    private int id;

    @JsonProperty("event_id")
    private int eventId;

    @JsonProperty("location")
    private Coordinates location;

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("type_name")
    private String typeName;

    @JsonProperty("sequence")
    private Integer sequence;

    @JsonProperty("radius")
    private Double radius;

    @JsonProperty("polygon_coordinates")
    private String polygonCoordinates;
}
