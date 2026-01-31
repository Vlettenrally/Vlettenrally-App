package nl.vlettenrally.data.domain;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Team entity representing a participating team
 */
public class Team {
    private UUID id;
    private UUID gameId;
    private String name;
    private String description;
    private String boatNumber;
    private String externalId;  // External ID from pre-registration system (em_booking_id)
    private List<String> participants;
    private UUID deviceId;
    private boolean checkedIn;
    private int totalPoints;
    private List<UUID> rivals;
    private String notes;
    private List<String> emergencyPhoneNumbers;
    private Instant createdAt;
    private Instant updatedAt;

    public Team() {
        this.id = UUID.randomUUID();
        this.participants = new ArrayList<>();
        this.rivals = new ArrayList<>();
        this.emergencyPhoneNumbers = new ArrayList<>();
        this.checkedIn = false;
        this.totalPoints = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Team(UUID id, UUID gameId, String name, String description, String boatNumber) {
        this();
        this.id = id;
        this.gameId = gameId;
        this.name = name;
        this.description = description;
        this.boatNumber = boatNumber;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getGameId() { return gameId; }
    public void setGameId(UUID gameId) { this.gameId = gameId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getBoatNumber() { return boatNumber; }
    public void setBoatNumber(String boatNumber) { this.boatNumber = boatNumber; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public List<String> getParticipants() { return participants; }
    public void setParticipants(List<String> participants) { this.participants = participants; }

    public UUID getDeviceId() { return deviceId; }
    public void setDeviceId(UUID deviceId) { this.deviceId = deviceId; }

    public boolean isCheckedIn() { return checkedIn; }
    public void setCheckedIn(boolean checkedIn) { this.checkedIn = checkedIn; }

    public int getTotalPoints() { return totalPoints; }
    public void setTotalPoints(int totalPoints) { this.totalPoints = totalPoints; }

    public List<UUID> getRivals() { return rivals; }
    public void setRivals(List<UUID> rivals) { this.rivals = rivals; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public List<String> getEmergencyPhoneNumbers() { return emergencyPhoneNumbers; }
    public void setEmergencyPhoneNumbers(List<String> emergencyPhoneNumbers) { 
        if (emergencyPhoneNumbers == null || emergencyPhoneNumbers.isEmpty()) {
            throw new IllegalArgumentException("At least one emergency phone number is required");
        }
        // Validate each phone number
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

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
