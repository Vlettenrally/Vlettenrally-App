package nl.vlettenrally.data.enums;

import lombok.Getter;

/**
 * Enum representing the type of JWT token being generated.
 * Used for logging and tracking purposes.
 */
@Getter
public enum TokenType {
    /**
     * Access token - short-lived token for API authentication
     */
    ACCESS("access"),

    /**
     * Refresh token - long-lived token for obtaining new access tokens
     */
    REFRESH("refresh"),

    /**
     * Check-in token - game-scoped token for team check-in process
     */
    CHECKIN("checkin");

    private final String value;

    TokenType(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }

    /**
     * Convert a string value to TokenType enum
     * @param value String value to convert
     * @return TokenType enum value
     * @throws IllegalArgumentException if value doesn't match any TokenType
     */
    public static TokenType fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("TokenType value cannot be null");
        }

        for (TokenType type : TokenType.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Unknown TokenType: " + value);
    }
}

