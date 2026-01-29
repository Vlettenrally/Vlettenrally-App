package nl.vlettenrally.provider;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Notification;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.FcmToken;
import nl.vlettenrally.data.domain.Message;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.enums.TransmissionChannel;
import nl.vlettenrally.data.repository.FcmTokenRepository;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * MessageTransmitter implementation for Firebase Cloud Messaging push notifications.
 */
@ApplicationScoped
public class FirebasePushTransmitter implements MessageTransmitter {

    private static final Logger LOG = Logger.getLogger(FirebasePushTransmitter.class);

    @ConfigProperty(name = "vlettenrally.firebase.enabled", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "vlettenrally.firebase.credentials-path", defaultValue = "")
    String credentialsPath;

    @Inject
    FcmTokenRepository fcmTokenRepository;

    private boolean initialized = false;

    @PostConstruct
    void init() {
        if (!enabled) {
            LOG.info("Firebase push notifications are disabled");
            return;
        }

        if (credentialsPath == null || credentialsPath.isBlank()) {
            LOG.warn("Firebase credentials path not configured, push notifications will be disabled");
            return;
        }

        try {
            FileInputStream serviceAccount = new FileInputStream(credentialsPath);
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }
            
            initialized = true;
            LOG.info("Firebase initialized successfully");
        } catch (IOException e) {
            LOG.error("Failed to initialize Firebase", e);
        }
    }

    @Override
    public TransmissionChannel getChannel() {
        return TransmissionChannel.PUSH;
    }

    @Override
    public boolean supports(Team team) {
        if (!enabled || !initialized) {
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

        if (!initialized) {
            return TransmissionResult.failure(TransmissionChannel.PUSH, "Firebase not initialized");
        }

        List<FcmToken> tokens = fcmTokenRepository.findByTeamId(team.getId());
        if (tokens.isEmpty()) {
            return TransmissionResult.skipped(TransmissionChannel.PUSH, "No FCM token for team");
        }

        // Send to all registered tokens for the team
        boolean anySuccess = false;
        String lastError = null;

        for (FcmToken fcmToken : tokens) {
            try {
                com.google.firebase.messaging.Message fcmMessage = com.google.firebase.messaging.Message.builder()
                        .setToken(fcmToken.getToken())
                        .setNotification(Notification.builder()
                                .setTitle(getNotificationTitle(message))
                                .setBody(message.getContent())
                                .build())
                        .putData("messageId", message.getId().toString())
                        .putData("messageType", message.getMessageType().name())
                        .putData("teamId", team.getId().toString())
                        .build();

                String response = FirebaseMessaging.getInstance().send(fcmMessage);
                LOG.infof("Push notification sent successfully: %s", response);
                anySuccess = true;
            } catch (FirebaseMessagingException e) {
                LOG.errorf("Failed to send push notification to token %s: %s", 
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
        };
    }
}
