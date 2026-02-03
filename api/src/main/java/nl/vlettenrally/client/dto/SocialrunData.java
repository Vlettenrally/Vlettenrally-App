package nl.vlettenrally.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Socialrun data structure from the location API.
 */
@RegisterForReflection
@NoArgsConstructor
@Getter
@Setter
public class SocialrunData {
    @JsonProperty("socialrunDetails")
    private List<SocialrunDetail> socialrunDetails;

    @JsonProperty("socialrunParticipantsLocations")
    private List<ParticipantLocation> participantLocations;

    @JsonProperty("socialrunParticipantsDistances")
    private List<Object> participantDistances;
}
