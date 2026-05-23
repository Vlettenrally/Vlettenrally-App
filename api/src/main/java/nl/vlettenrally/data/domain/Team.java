package nl.vlettenrally.data.domain;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Team entity representing a participating team
 */
@Getter
@Setter
public class Team {
    private UUID id;
    private UUID gameId;
    private String name;
    private String description;
    private String externalId;  // External ID from pre-registration system (ticket_booking_id)
    private List<String> participants;
    private UUID deviceId;
    private boolean checkedIn;
    private boolean checkedInDigitally;
    private int totalPoints;
    private List<UUID> rivals;
    private String notes;
    
    @Setter(AccessLevel.NONE)
    private List<String> emergencyPhoneNumbers;
    
    private Instant createdAt;
    private Instant updatedAt;

    public Team() {
        this.id = UUID.randomUUID();
        this.participants = new ArrayList<>();
        this.rivals = new ArrayList<>();
        this.emergencyPhoneNumbers = new ArrayList<>();
        this.checkedIn = false;
        this.checkedInDigitally = false;
        this.totalPoints = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Team(UUID id, UUID gameId, String name, String description) {
        this();
        this.id = id;
        this.gameId = gameId;
        this.name = name;
        this.description = description;
    }

    public void setEmergencyPhoneNumbers(List<String> emergencyPhoneNumbers) { 
        if (emergencyPhoneNumbers == null) {
            this.emergencyPhoneNumbers = new ArrayList<>();
            return;
        }
        // Validate format of each phone number if provided
        for (String phoneNumber : emergencyPhoneNumbers) {
            if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
                throw new IllegalArgumentException("Emergency phone number cannot be null or empty");
            }
            if (!isValidPhoneNumber(phoneNumber.trim())) {
                throw new IllegalArgumentException("Invalid phone number format: " + phoneNumber);
            }
        }
        this.emergencyPhoneNumbers = emergencyPhoneNumbers; 
    }

    public void addEmergencyPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Emergency phone number cannot be null or empty");
        }
        if (!isValidPhoneNumber(phoneNumber.trim())) {
            throw new IllegalArgumentException("Invalid phone number format: " + phoneNumber);
        }
        this.emergencyPhoneNumbers.add(phoneNumber.trim());
    }

    /**
     * Validates phone number format using Google's libphonenumber library
     * Supports international phone numbers with proper validation
     */
    private boolean isValidPhoneNumber(String phoneNumber) {
        PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        try {
            // Default to Netherlands region for numbers without country code
            PhoneNumber numberProto = phoneUtil.parse(phoneNumber, "NL");
            return phoneUtil.isValidNumber(numberProto);
        } catch (NumberParseException e) {
            return false;
        }
    }
}
