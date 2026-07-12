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
    // Deprecated: kept for FCM/legacy paths. Coordination uses mainDeviceId + DeviceRegistration rows.
    private UUID deviceId;
    private UUID mainDeviceId;
    private boolean checkedIn;
    private boolean checkedInDigitally;
    // Timestamp of the team's physical check-in (when checkedIn transitions to true).
    // The digital check-in window (first phone registration) is measured from here.
    // Cleared when checkedIn is reset to false. See CheckInCoordinationService.
    private Instant checkedInAt;
    // Set once the team reaches its finish zone (via the FINISH_TEAM automation
    // action or an admin override). A finished team keeps its points and ranking
    // but stops accruing automatic points (checkpoint passes + distance).
    private boolean finished;
    private Instant finishedAt;
    private List<UUID> rivals;
    private String notes;
    // Traccar GPS tracking: opt-in per team. traccarIdentifier is a long-lived
    // secret the Traccar Client app sends to the ingestion endpoint; generated
    // when tracking is first enabled and kept across enable/disable toggles.
    private boolean traccarEnabled;
    private String traccarIdentifier;
    // Physical GPS tracker ID from the external provider, auto-assigned in bulk
    // and exported back to them. Independent of traccarIdentifier above.
    private String trackerId;
    // When trackerId was last (re)assigned; lower bound for location backfill so a
    // reassigned tracker's earlier movement (recorded for another team) is never
    // imported. Cleared with trackerId; re-assigning the same tracker after a clear
    // re-stamps (while unassigned it may have served another team). Null = no bound.
    private Instant trackerAssignedAt;

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
        this.finished = false;
        this.traccarEnabled = false;
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
