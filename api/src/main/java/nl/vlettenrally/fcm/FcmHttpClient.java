package nl.vlettenrally.fcm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * HTTP client for sending push notifications via Firebase Cloud Messaging REST API v1.
 * 
 * This client uses the FCM v1 API which requires OAuth2 authentication.
 * See: https://firebase.google.com/docs/cloud-messaging/send-message
 */
@ApplicationScoped
public class FcmHttpClient {

    private static final Logger LOG = Logger.getLogger(FcmHttpClient.class);
    
    private static final String FCM_ENDPOINT_TEMPLATE = "https://fcm.googleapis.com/v1/projects/%s/messages:send";
    
    @ConfigProperty(name = "vlettenrally.firebase.enabled", defaultValue = "false")
    boolean enabled;
    
    @Inject
    GoogleOAuth2Authenticator authenticator;
    
    private final HttpClient httpClient = HttpClient.newHttpClient();
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
            throw new FcmException("Firebase is disabled");
        }
        
        if (!authenticator.isInitialized()) {
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
                    .build();
            
            // Send request
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            // Handle response
            if (response.statusCode() == 200) {
                JsonNode responseJson = objectMapper.readTree(response.body());
                String messageId = responseJson.get("name").asText();
                LOG.debugf("FCM notification sent successfully: %s", messageId);
                return messageId;
            } else {
                String errorMessage = parseErrorMessage(response.body());
                throw new FcmException("FCM request failed: " + response.statusCode() + " - " + errorMessage);
            }
            
        } catch (IOException e) {
            throw new FcmException("Failed to send FCM notification: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FcmException("FCM request interrupted", e);
        }
    }
    
    /**
     * Build FCM message payload in JSON format.
     */
    private String buildFcmPayload(String fcmToken, String title, String body, java.util.Map<String, String> data) 
            throws IOException {
        
        var messageNode = objectMapper.createObjectNode();
        var message = messageNode.putObject("message");
        
        // Set token
        message.put("token", fcmToken);
        
        // Set notification
        var notification = message.putObject("notification");
        notification.put("title", title);
        notification.put("body", body);
        
        // Set data payload
        if (data != null && !data.isEmpty()) {
            var dataNode = message.putObject("data");
            data.forEach(dataNode::put);
        }
        
        return objectMapper.writeValueAsString(messageNode);
    }
    
    /**
     * Parse error message from FCM error response.
     */
    private String parseErrorMessage(String responseBody) {
        try {
            JsonNode errorJson = objectMapper.readTree(responseBody);
            if (errorJson.has("error")) {
                JsonNode error = errorJson.get("error");
                if (error.has("message")) {
                    return error.get("message").asText();
                }
            }
            return responseBody;
        } catch (Exception e) {
            return responseBody;
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
