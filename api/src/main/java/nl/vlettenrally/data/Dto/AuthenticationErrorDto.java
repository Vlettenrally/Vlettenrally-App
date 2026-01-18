package nl.vlettenrally.data.Dto;

import lombok.Data;

@Data
public class AuthenticationErrorDto {
    private String message;

    public AuthenticationErrorDto(String message) {
        this.message = message;
    }
}