package nl.vlettenrally.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response from the location tracking API.
 */
@RegisterForReflection
@NoArgsConstructor
@Getter
@Setter
public class LocationResponse {
    @JsonProperty("socialrunParticipantsLocations")
    private SocialrunData socialrunParticipantsLocations;
}
