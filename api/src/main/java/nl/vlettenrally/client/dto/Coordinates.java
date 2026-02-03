package nl.vlettenrally.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Geographic coordinates.
 */
@RegisterForReflection
@NoArgsConstructor
@Getter
@Setter
public class Coordinates {
    @JsonProperty("x")
    private double longitude;

    @JsonProperty("y")
    private double latitude;
}
