package nl.vlettenrally.data.Dto;

public class TokenResponseDto {
    public String accessToken;
    public String refreshToken;
    public long expiresIn; // seconds until access token expires
    public String tokenType = "Bearer";
    
    public TokenResponseDto(String accessToken, String refreshToken, long expiresIn) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
    }
}
