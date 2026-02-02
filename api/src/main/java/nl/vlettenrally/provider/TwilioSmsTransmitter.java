package nl.vlettenrally.provider;

import com.twilio.Twilio;
import com.twilio.exception.TwilioException;
import com.twilio.rest.api.v2010.account.Message.Status;
import com.twilio.type.PhoneNumber;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import nl.vlettenrally.data.domain.Message;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.enums.TransmissionChannel;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

/**
 * MessageTransmitter implementation for Twilio SMS messages.
 * Only used for critical messages when team is inactive.
 */
@ApplicationScoped
public class TwilioSmsTransmitter implements MessageTransmitter {

    @ConfigProperty(name = "vlettenrally.twilio.enabled", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "vlettenrally.twilio.account-sid", defaultValue = "")
    String accountSid;

    @ConfigProperty(name = "vlettenrally.twilio.api-key-sid", defaultValue = "")
    String apiKeySid;

    @ConfigProperty(name = "vlettenrally.twilio.api-key-secret", defaultValue = "")
    String apiKeySecret;

    @ConfigProperty(name = "vlettenrally.twilio.from-number", defaultValue = "")
    String fromNumber;

    private boolean initialized = false;

    @PostConstruct
    void init() {
        if (!enabled) {
            Log.info("Twilio SMS is disabled");
            return;
        }

        if (accountSid == null || accountSid.isBlank() ||
            apiKeySid == null || apiKeySid.isBlank() ||
            apiKeySecret == null || apiKeySecret.isBlank() ||
            fromNumber == null || fromNumber.isBlank()) {
            Log.warn("Twilio credentials not fully configured, SMS will be disabled");
            return;
        }

        try {
            Twilio.init(apiKeySid, apiKeySecret, accountSid);
            initialized = true;
            Log.info("Twilio initialized successfully with API key");
        } catch (Exception e) {
            Log.error("Failed to initialize Twilio", e);
        }
    }

    @Override
    public TransmissionChannel getChannel() {
        return TransmissionChannel.SMS;
    }

    @Override
    public boolean supports(Team team) {
        if (!enabled || !initialized) {
            return false;
        }
        
        List<String> phoneNumbers = team.getEmergencyPhoneNumbers();
        return phoneNumbers != null && !phoneNumbers.isEmpty();
    }

    @Override
    public TransmissionResult transmit(Message message, Team team) {
        if (!enabled) {
            return TransmissionResult.skipped(TransmissionChannel.SMS, "Twilio is disabled");
        }

        if (!initialized) {
            return TransmissionResult.failure(TransmissionChannel.SMS, "Twilio not initialized");
        }

        List<String> phoneNumbers = team.getEmergencyPhoneNumbers();
        if (phoneNumbers == null || phoneNumbers.isEmpty()) {
            return TransmissionResult.skipped(TransmissionChannel.SMS, "No phone number for team");
        }

        // Send to first emergency contact only
        String toNumber = phoneNumbers.get(0);

        try {
            String smsBody = formatSmsMessage(message, team);
            
            com.twilio.rest.api.v2010.account.Message twilioMessage = 
                    com.twilio.rest.api.v2010.account.Message.creator(
                            new PhoneNumber(toNumber),
                            new PhoneNumber(fromNumber),
                            smsBody
                    ).create();

            Status status = twilioMessage.getStatus();
            Log.infof("SMS sent to %s, status: %s, SID: %s", 
                    toNumber, status, twilioMessage.getSid());

            if (status == Status.FAILED || status == Status.UNDELIVERED) {
                return TransmissionResult.failure(TransmissionChannel.SMS, 
                        "SMS delivery failed: " + status);
            }

            return TransmissionResult.success(TransmissionChannel.SMS);
        } catch (TwilioException e) {
            Log.errorf("Failed to send SMS to %s: %s", toNumber, e.getMessage());
            return TransmissionResult.failure(TransmissionChannel.SMS, e.getMessage());
        }
    }

    private String formatSmsMessage(Message message, Team team) {
        String prefix = switch (message.getMessageType()) {
            case RALLYDESK -> "[RALLYDESK] ";
            case CHECKPOINT -> "[CHECKPOINT] ";
            default -> "[Vlettenrally] ";
        };

        String body = prefix + message.getContent();
        
        // SMS has 160 character limit for single segment
        if (body.length() > 160) {
            body = body.substring(0, 157) + "...";
        }
        
        return body;
    }
}
