package nl.vlettenrally.provider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.FcmToken;
import nl.vlettenrally.data.domain.Message;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.enums.TransmissionChannel;
import nl.vlettenrally.data.repository.FcmTokenRepository;
import nl.vlettenrally.fcm.FcmHttpClient;
import nl.vlettenrally.observability.DeliveryMetrics;
import nl.vlettenrally.observability.DeliveryMetrics.FcmCaller;

/**
 * MessageTransmitter implementation for Firebase Cloud Messaging push notifications.
 * Uses custom HTTP client instead of Firebase Admin SDK for better GraalVM compatibility.
 */
@ApplicationScoped
public class FirebasePushTransmitter implements MessageTransmitter {

    @ConfigProperty(name = "vlettenrally.firebase.enabled", defaultValue = "false")
    boolean enabled;

    @Inject
    FcmTokenRepository fcmTokenRepository;
    
    @Inject
    FcmHttpClient fcmHttpClient;

    @Inject
    DeliveryMetrics metrics;

    @Override
    public TransmissionChannel getChannel() {
        return TransmissionChannel.PUSH;
    }

    @Override
    public boolean supports(Team team) {
        if (!enabled) {
            return false;
        }
        
        List<FcmToken> tokens = fcmTokenRepository.findByTeamId(team.getId());
        return !tokens.isEmpty();
    }

    @Override
    public TransmissionResult transmit(Message message, Team team) {
        if (!enabled) {
            return TransmissionResult.skipped(TransmissionChannel.PUSH, "Firebase is disabled");
        }

        List<FcmToken> tokens = fcmTokenRepository.findByTeamId(team.getId());
        if (tokens.isEmpty()) {
            return TransmissionResult.skipped(TransmissionChannel.PUSH, "No FCM token for team");
        }

        // Build data payload
        Map<String, String> data = new HashMap<>();
        data.put("messageId", message.getId().toString());
        data.put("messageType", message.getMessageType().name());
        data.put("teamId", team.getId().toString());

        // Send to all registered tokens for the team. One logical message can cost several FCM
        // calls; fcm.fanout over msg.transmit{channel=push} is the amplification factor, which
        // the anySuccess/lastError collapse below otherwise hides completely.
        metrics.fcmFanout(FcmCaller.TEAM_MESSAGE, tokens.size());
        boolean anySuccess = false;
        String lastError = null;

        for (FcmToken fcmToken : tokens) {
            try {
                String messageId = fcmHttpClient.sendNotification(
                        fcmToken.getToken(),
                        getNotificationTitle(message),
                        message.getContent(),
                        data
                );

                Log.infof("Push notification sent successfully: %s", messageId);
                anySuccess = true;
            } catch (FcmHttpClient.FcmException e) {
                Log.errorf("Failed to send push notification to token %s: %s",
                        fcmToken.getToken().substring(0, 10) + "...", e.getMessage());
                lastError = e.getMessage();
            }
        }

        if (anySuccess) {
            return TransmissionResult.success(TransmissionChannel.PUSH);
        } else {
            return TransmissionResult.failure(TransmissionChannel.PUSH,
                    lastError != null ? lastError : "Unknown error");
        }
    }

    private String getNotificationTitle(Message message) {
        return switch (message.getMessageType()) {
            case GENERAL -> "Vlettenrally";
            case COUNTDOWN -> "⏱️ Countdown";
            case CHECKPOINT -> "🏁 Checkpoint";
            case RALLYDESK -> "📢 Rallydesk";
            case ACHIEVEMENT -> "🏆 Achievement";
            case RIVAL -> "⚔️ Rivaal Update";
            case SPEED_WARNING -> "⚠️ Snelheidsmelding";
        };
    }
}
