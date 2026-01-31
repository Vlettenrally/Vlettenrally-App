package nl.vlettenrally.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Socialrun data structure from the location API.
 */
public class SocialrunData {
    @JsonProperty("socialrunDetails")
    private List<SocialrunDetail> socialrunDetails;

    @JsonProperty("socialrunParticipantsLocations")
    private List<ParticipantLocation> participantLocations;

    @JsonProperty("socialrunParticipantsDistances")
    private List<Object> participantDistances;

    public List<SocialrunDetail> getSocialrunDetails() {
        return socialrunDetails;
    }

    public void setSocialrunDetails(List<SocialrunDetail> socialrunDetails) {
        this.socialrunDetails = socialrunDetails;
    }

    public List<ParticipantLocation> getParticipantLocations() {
        return participantLocations;
    }

    public void setParticipantLocations(List<ParticipantLocation> participantLocations) {
        this.participantLocations = participantLocations;
    }

    public List<Object> getParticipantDistances() {
        return participantDistances;
    }

    public void setParticipantDistances(List<Object> participantDistances) {
        this.participantDistances = participantDistances;
    }
}
