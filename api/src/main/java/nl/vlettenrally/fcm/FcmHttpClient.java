package nl.vlettenrally.fcm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.observability.DeliveryMetrics;
import nl.vlettenrally.observability.DeliveryMetrics.FcmOutcome;

/**
 * HTTP client for sending push notifications via Firebase Cloud Messaging REST API v1.
 * This client uses the FCM v1 API which requires OAuth2 authentication.
 * See: <a href="https://firebase.google.com/docs/cloud-messaging/send-message">...</a>
 */
@ApplicationScoped
public class FcmHttpClient {
    
    private static final String FCM_ENDPOINT_TEMPLATE = "https://fcm.googleapis.com/v1/projects/%s/messages:send";
    
    /**
     * Sends are synchronous and run inside the per-team transmitter loop, so an unbounded call
     * would stall every remaining team in a batch behind one hung request.
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    @ConfigProperty(name = "vlettenrally.firebase.enabled", defaultValue = "false")
    boolean enabled;

    @Inject
    GoogleOAuth2Authenticator authenticator;

    @Inject
    DeliveryMetrics metrics;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Send a push notification via FCM.
     * 
     * @param fcmToken The device FCM token
     * @param title Notification title
     * @param body Notification body
     * @param data Additional data payload
     * @return FCM message ID if successful
     * @throws FcmException if sending fails
     */
    public String sendNotification(String fcmToken, String title, String body, java.util.Map<String, String> data) 
            throws FcmException {
        
        if (!enabled) {
            metrics.fcmRequest(FcmOutcome.DISABLED);
            throw new FcmException("Firebase is disabled");
        }

        if (!authenticator.isInitialized()) {
            metrics.fcmRequest(FcmOutcome.AUTH_ERROR);
            throw new FcmException("Firebase authenticator not initialized");
        }

        try {
            // Get valid access token (will refresh if needed)
            String accessToken = authenticator.getAccessToken();
            String projectId = authenticator.getProjectId();
            
            // Build FCM message payload
            String jsonPayload = buildFcmPayload(fcmToken, title, body, data);
            
            // Build HTTP request
            String fcmEndpoint = String.format(FCM_ENDPOINT_TEMPLATE, projectId);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fcmEndpoint))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                    .timeout(REQUEST_TIMEOUT)
                    .build();

            // Send request
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // Handle response
            if (response.statusCode() == 200) {
                metrics.fcmRequest(FcmOutcome.OK);
                JsonNode responseJson = objectMapper.readTree(response.body());
                String messageId = responseJson.get("name").asText();
                Log.debugf("FCM notification sent successfully: %s", messageId);
                return messageId;
            } else {
                FcmError error = parseError(response.body());
                metrics.fcmRequest(classify(response.statusCode(), error.code()));
                throw new FcmException("FCM request failed: " + response.statusCode() + " - " + error.message());
            }

        } catch (IOException e) {
            metrics.fcmRequest(FcmOutcome.TRANSPORT_ERROR);
            throw new FcmException("Failed to send FCM notification: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            metrics.fcmRequest(FcmOutcome.TRANSPORT_ERROR);
            throw new FcmException("FCM request interrupted", e);
        }
    }

    /**
     * Map an FCM failure onto a metric outcome.
     * <p>
     * The distinction matters operationally and is invisible from the exception message alone:
     * {@code UNREGISTERED} means the device token is dead and should be pruned, {@code RATE_LIMITED}
     * means back off, {@code UNAVAILABLE} means retry later, and an auth error means the service
     * account needs attention. Prefers the structured error code, falling back to the HTTP status.
     */
    public static FcmOutcome classify(int statusCode, String errorCode) {
        String code = errorCode == null ? "" : errorCode.toUpperCase(Locale.ROOT);
        return switch (code) {
            case "UNREGISTERED", "NOT_FOUND" -> FcmOutcome.UNREGISTERED;
            case "INVALID_ARGUMENT", "SENDER_ID_MISMATCH" -> FcmOutcome.INVALID_ARGUMENT;
            case "QUOTA_EXCEEDED", "RESOURCE_EXHAUSTED" -> FcmOutcome.RATE_LIMITED;
            case "UNAVAILABLE", "INTERNAL" -> FcmOutcome.UNAVAILABLE;
            case "UNAUTHENTICATED", "THIRD_PARTY_AUTH_ERROR", "PERMISSION_DENIED" -> FcmOutcome.AUTH_ERROR;
            default -> switch (statusCode) {
                case 400 -> FcmOutcome.INVALID_ARGUMENT;
                case 401, 403 -> FcmOutcome.AUTH_ERROR;
                case 404 -> FcmOutcome.UNREGISTERED;
                case 429 -> FcmOutcome.RATE_LIMITED;
                case 500, 502, 503, 504 -> FcmOutcome.UNAVAILABLE;
                default -> FcmOutcome.OTHER;
            };
        };
    }
    
    /**
     * Build FCM message payload in JSON format.
     * Uses data-only messages to prevent duplicate notifications on web clients.
     * The notification fields are sent as data so the service worker can display them.
     */
    private String buildFcmPayload(String fcmToken, String title, String body, java.util.Map<String, String> data) 
            throws IOException {
        
        var messageNode = objectMapper.createObjectNode();
        var message = messageNode.putObject("message");
        
        // Set token
        message.put("token", fcmToken);
        
        var dataNode = message.putObject("data");
        dataNode.put("title", title);
        dataNode.put("body", body);

        // Add additional data payload
        if (data != null && !data.isEmpty()) {
            data.forEach(dataNode::put);
        }
        
        return objectMapper.writeValueAsString(messageNode);
    }
    
    /** A parsed FCM error response: the human-readable message plus its machine-readable code. */
    record FcmError(String message, String code) {}

    /**
     * Parse an FCM error response.
     * <p>
     * The structured code lives in {@code error.details[]} under the
     * {@code google.firebase.fcm.v1.FcmError} entry; {@code error.status} is the coarser gRPC
     * status. Both are kept because only the former distinguishes a dead token from a bad payload.
     */
    private FcmError parseError(String responseBody) {
        try {
            JsonNode errorJson = objectMapper.readTree(responseBody);
            JsonNode error = errorJson.get("error");
            if (error == null) {
                return new FcmError(responseBody, null);
            }
            String message = error.has("message") ? error.get("message").asText() : responseBody;

            String code = null;
            JsonNode details = error.get("details");
            if (details != null && details.isArray()) {
                for (JsonNode detail : details) {
                    if (detail.hasNonNull("errorCode")) {
                        code = detail.get("errorCode").asText();
                        break;
                    }
                }
            }
            if (code == null && error.hasNonNull("status")) {
                code = error.get("status").asText();
            }
            return new FcmError(message, code);
        } catch (Exception e) {
            return new FcmError(responseBody, null);
        }
    }
    
    /**
     * Exception thrown when FCM operations fail.
     */
    public static class FcmException extends Exception {
        public FcmException(String message) {
            super(message);
        }
        
        public FcmException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
