package nl.vlettenrally.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from the location tracking API.
 */
public class LocationResponse {
    @JsonProperty("socialrunParticipantsLocations")
    private SocialrunData socialrunParticipantsLocations;

    public SocialrunData getSocialrunParticipantsLocations() {
        return socialrunParticipantsLocations;
    }

    public void setSocialrunParticipantsLocations(SocialrunData socialrunParticipantsLocations) {
        this.socialrunParticipantsLocations = socialrunParticipantsLocations;
    }
}
