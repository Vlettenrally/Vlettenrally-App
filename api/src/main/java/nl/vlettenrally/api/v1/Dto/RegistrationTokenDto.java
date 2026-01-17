package nl.vlettenrally.api.v1.Dto;

public class RegistrationTokenDto {
    public String registrationToken;
    public String teamId;
    public long expiresAt; // Unix timestamp in seconds
    
    public RegistrationTokenDto(String registrationToken, String teamId, long expiresAt) {
        this.registrationToken = registrationToken;
        this.teamId = teamId;
        this.expiresAt = expiresAt;
    }
}
