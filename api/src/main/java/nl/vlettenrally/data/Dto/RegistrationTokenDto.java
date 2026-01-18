package nl.vlettenrally.data.Dto;

import java.time.Instant;

public class RegistrationTokenDto {
    public String registrationToken;
    public String teamId;
    public Instant expiresAt; // Unix timestamp in seconds
    
    public RegistrationTokenDto(String registrationToken, String teamId, Instant expiresAt) {
        this.registrationToken = registrationToken;
        this.teamId = teamId;
        this.expiresAt = expiresAt;
    }
}
