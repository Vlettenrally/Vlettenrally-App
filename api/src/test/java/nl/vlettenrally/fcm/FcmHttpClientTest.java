package nl.vlettenrally.fcm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.vlettenrally.observability.DeliveryMetrics;

/**
 * Unit tests for FcmHttpClient.
 * Tests the FCM message payload structure to ensure data-only messages are sent
 * to prevent duplicate notifications on web clients.
 */
@ExtendWith(MockitoExtension.class)
class FcmHttpClientTest {

    @Mock
    private GoogleOAuth2Authenticator authenticator;

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    @Mock
    private DeliveryMetrics metrics;

    private FcmHttpClient fcmHttpClient;

    private static final Integer EPHEMERAL_TTL_SECONDS = 900;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        fcmHttpClient = new FcmHttpClient();

        // Use reflection to inject mocks and set up the client
        setField(fcmHttpClient, "enabled", true);
        setField(fcmHttpClient, "authenticator", authenticator);
        setField(fcmHttpClient, "httpClient", httpClient);
        setField(fcmHttpClient, "objectMapper", objectMapper);
        setField(fcmHttpClient, "metrics", metrics);
    }

    @Test
    void testBuildFcmPayload_DataOnlyMessage() throws Exception {
        // Arrange
        String fcmToken = "mock-fcm-token";
        String title = "Test Notification";
        String body = "This is a test message";
        Map<String, String> data = new HashMap<>();
        data.put("messageId", "123");
        data.put("messageType", "GENERAL");
        data.put("teamId", "456");

        // Act - Call private method using reflection
        String payloadJson = invokeBuildFcmPayload(fcmToken, title, body, data);

        // Assert - Parse and verify payload structure
        JsonNode payload = objectMapper.readTree(payloadJson);
        JsonNode message = payload.get("message");
        assertNotNull(message, "Payload must have a 'message' field");

        // CRITICAL: Verify NO notification field exists (prevents duplicate notifications)
        assertNull(message.get("notification"),
                   "Payload must NOT contain 'notification' field - this prevents duplicate notifications on web clients");

        // Verify token
        assertEquals(fcmToken, message.get("token").asText(), "Token must be set correctly");

        // Verify data field exists and contains title/body
        JsonNode dataNode = message.get("data");
        assertNotNull(dataNode, "Payload must contain 'data' field");
        assertEquals(title, dataNode.get("title").asText(),
                     "Title must be in data field (not notification field)");
        assertEquals(body, dataNode.get("body").asText(),
                     "Body must be in data field (not notification field)");

        // Verify additional data fields are preserved
        assertEquals("123", dataNode.get("messageId").asText());
        assertEquals("GENERAL", dataNode.get("messageType").asText());
        assertEquals("456", dataNode.get("teamId").asText());

        // Verify data has exactly 6 fields (title, body, messageId, messageType, teamId, sentAt)
        assertEquals(6, dataNode.size());
    }

    @Test
    void testBuildFcmPayload_HighUrgencyWithTtl() throws Exception {
        // Act
        String payloadJson = invokeBuildFcmPayload(
            "mock-fcm-token", "Test Title", "Test Body", null, PushUrgency.HIGH, 900);

        // Assert
        JsonNode headers = webpushHeaders(payloadJson);

        // CRITICAL: at normal urgency the push service defers delivery while the device is
        // idle, which shows up as notifications arriving minutes after they were sent.
        assertEquals("high", headers.get("Urgency").asText(),
                     "High urgency must reach the wire as 'high' so Android does not wait for the next Doze window");
        assertEquals("900", headers.get("TTL").asText(),
                     "Ephemeral notifications must expire rather than surface stale");
    }

    @Test
    void testBuildFcmPayload_NormalUrgency() throws Exception {
        // Act - GENERAL messages ride along with whatever wakeup the device was going to do anyway
        String payloadJson = invokeBuildFcmPayload(
            "mock-fcm-token", "Test Title", "Test Body", null, PushUrgency.NORMAL, null);

        // Assert
        assertEquals("normal", webpushHeaders(payloadJson).get("Urgency").asText(),
                     "Normal urgency must reach the wire as 'normal', letting the device batch the wakeup");
    }

    @Test
    void testBuildFcmPayload_NullTtlOmitsHeader() throws Exception {
        // Act - a null TTL means "deliver whenever it lands", used for everything that is not ephemeral
        String payloadJson = invokeBuildFcmPayload(
            "mock-fcm-token", "Test Title", "Test Body", null, PushUrgency.HIGH, null);

        // Assert - a TTL header would silently discard the message, so it must be absent
        // entirely rather than sent with a large value.
        JsonNode headers = webpushHeaders(payloadJson);
        assertNull(headers.get("TTL"),
                   "Non-ephemeral notifications must not carry a TTL - expiry would drop them");
        assertEquals("high", headers.get("Urgency").asText(),
                     "Urgency and TTL are independent: dropping the TTL must not downgrade urgency");
    }

    private JsonNode webpushHeaders(String payloadJson) throws Exception {
        return objectMapper.readTree(payloadJson).get("message").path("webpush").path("headers");
    }

    @Test
    void testBuildFcmPayload_StampsSentAt() throws Exception {
        // Arrange
        long before = System.currentTimeMillis();

        // Act
        String payloadJson = invokeBuildFcmPayload("mock-fcm-token", "Test Title", "Test Body", null);

        // Assert - the service worker subtracts this from its own clock to report delivery latency
        JsonNode dataNode = objectMapper.readTree(payloadJson).get("message").get("data");
        long sentAt = Long.parseLong(dataNode.get("sentAt").asText());

        assertTrue(sentAt >= before && sentAt <= System.currentTimeMillis(),
                   "sentAt must be the wall-clock send time in epoch millis");
    }

    @Test
    void testBuildFcmPayload_SentAtNotShadowedByCallerData() throws Exception {
        // Arrange - a caller passing its own sentAt must not be able to skew the latency metric
        Map<String, String> data = new HashMap<>();
        data.put("sentAt", "1");

        // Act
        String payloadJson = invokeBuildFcmPayload("mock-fcm-token", "Test Title", "Test Body", data);

        // Assert
        JsonNode dataNode = objectMapper.readTree(payloadJson).get("message").get("data");
        assertNotEquals("1", dataNode.get("sentAt").asText(),
                        "sentAt must be written after the caller's data map, not overwritten by it");
    }

    @Test
    void testBuildFcmPayload_WithNullData() throws Exception {
        // Arrange
        String fcmToken = "mock-fcm-token";
        String title = "Test Title";
        String body = "Test Body";

        // Act
        String payloadJson = invokeBuildFcmPayload(fcmToken, title, body, null);

        // Assert
        JsonNode payload = objectMapper.readTree(payloadJson);
        JsonNode message = payload.get("message");
        JsonNode dataNode = message.get("data");

        assertNotNull(dataNode);
        assertEquals(title, dataNode.get("title").asText());
        assertEquals(body, dataNode.get("body").asText());
        // Should only have title, body and sentAt when additional data is null
        assertEquals(3, dataNode.size());
    }

    @Test
    void testBuildFcmPayload_WithEmptyData() throws Exception {
        // Arrange
        String fcmToken = "mock-fcm-token";
        String title = "Test Title";
        String body = "Test Body";
        Map<String, String> data = new HashMap<>();

        // Act
        String payloadJson = invokeBuildFcmPayload(fcmToken, title, body, data);

        // Assert
        JsonNode payload = objectMapper.readTree(payloadJson);
        JsonNode message = payload.get("message");
        JsonNode dataNode = message.get("data");

        assertNotNull(dataNode);
        assertEquals(title, dataNode.get("title").asText());
        assertEquals(body, dataNode.get("body").asText());
        // Should only have title, body and sentAt when additional data is empty
        assertEquals(3, dataNode.size());
    }

    @Test
    void testSendNotification_Success() throws Exception {
        // Arrange
        when(authenticator.isInitialized()).thenReturn(true);
        when(authenticator.getAccessToken()).thenReturn("mock-access-token");
        when(authenticator.getProjectId()).thenReturn("mock-project-id");

        String fcmToken = "mock-fcm-token";
        String title = "Test Title";
        String body = "Test Body";
        Map<String, String> data = new HashMap<>();
        data.put("messageId", "123");

        String responseJson = "{\"name\":\"projects/mock-project-id/messages/msg-12345\"}";
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(responseJson);
        when(httpClient.send(any(HttpRequest.class), anyBodyHandler()))
                .thenReturn(httpResponse);

        // Act
        String messageId = fcmHttpClient.sendNotification(fcmToken, title, body, data, PushUrgency.HIGH, EPHEMERAL_TTL_SECONDS);

        // Assert
        assertEquals("projects/mock-project-id/messages/msg-12345", messageId);
        verify(httpClient).send(any(HttpRequest.class), anyBodyHandler());
    }

    @Test
    void testSendNotification_FirebaseDisabled() {
        // Arrange
        setField(fcmHttpClient, "enabled", false);

        // Act & Assert
        FcmHttpClient.FcmException exception = assertThrows(
            FcmHttpClient.FcmException.class,
            () -> fcmHttpClient.sendNotification("token", "title", "body", null, PushUrgency.HIGH, EPHEMERAL_TTL_SECONDS)
        );
        assertEquals("Firebase is disabled", exception.getMessage());
    }

    @Test
    void testSendNotification_AuthenticatorNotInitialized() {
        // Arrange
        when(authenticator.isInitialized()).thenReturn(false);

        // Act & Assert
        FcmHttpClient.FcmException exception = assertThrows(
            FcmHttpClient.FcmException.class,
            () -> fcmHttpClient.sendNotification("token", "title", "body", null, PushUrgency.HIGH, EPHEMERAL_TTL_SECONDS)
        );
        assertEquals("Firebase authenticator not initialized", exception.getMessage());
    }

    @Test
    void testSendNotification_HttpError() throws Exception {
        // Arrange
        when(authenticator.isInitialized()).thenReturn(true);
        when(authenticator.getAccessToken()).thenReturn("mock-access-token");
        when(authenticator.getProjectId()).thenReturn("mock-project-id");

        String errorResponse = "{\"error\":{\"message\":\"Invalid FCM token\"}}";
        when(httpResponse.statusCode()).thenReturn(400);
        when(httpResponse.body()).thenReturn(errorResponse);
        when(httpClient.send(any(HttpRequest.class), anyBodyHandler()))
                .thenReturn(httpResponse);

        // Act & Assert
        FcmHttpClient.FcmException exception = assertThrows(
            FcmHttpClient.FcmException.class,
            () -> fcmHttpClient.sendNotification("token", "title", "body", null, PushUrgency.HIGH, EPHEMERAL_TTL_SECONDS)
        );
        assertTrue(exception.getMessage().contains("400"));
        assertTrue(exception.getMessage().contains("Invalid FCM token"));
    }

    @Test
    void testSendNotification_IOException() throws Exception {
        // Arrange
        when(authenticator.isInitialized()).thenReturn(true);
        when(authenticator.getAccessToken()).thenReturn("mock-access-token");
        when(authenticator.getProjectId()).thenReturn("mock-project-id");

        when(httpClient.send(any(HttpRequest.class), anyBodyHandler()))
                .thenThrow(new IOException("Network error"));

        // Act & Assert
        FcmHttpClient.FcmException exception = assertThrows(
            FcmHttpClient.FcmException.class,
            () -> fcmHttpClient.sendNotification("token", "title", "body", null, PushUrgency.HIGH, EPHEMERAL_TTL_SECONDS)
        );
        assertTrue(exception.getMessage().contains("Network error"));
    }

    @Test
    void testSendNotification_InterruptedException() throws Exception {
        // Arrange
        when(authenticator.isInitialized()).thenReturn(true);
        when(authenticator.getAccessToken()).thenReturn("mock-access-token");
        when(authenticator.getProjectId()).thenReturn("mock-project-id");

        when(httpClient.send(any(HttpRequest.class), anyBodyHandler()))
                .thenThrow(new InterruptedException("Thread interrupted"));

        // Act & Assert
        FcmHttpClient.FcmException exception = assertThrows(
            FcmHttpClient.FcmException.class,
            () -> fcmHttpClient.sendNotification("token", "title", "body", null, PushUrgency.HIGH, EPHEMERAL_TTL_SECONDS)
        );
        assertTrue(exception.getMessage().contains("interrupted"));
        assertTrue(Thread.currentThread().isInterrupted());
        // Clear interrupt flag for other tests
        Thread.interrupted();
    }

    // Helper methods

    /**
     * Matcher for send()'s body handler, typed so the stubbed send() resolves to
     * HttpResponse&lt;String&gt; instead of going raw.
     */
    private static HttpResponse.BodyHandler<String> anyBodyHandler() {
        return ArgumentMatchers.any();
    }

    /**
     * Use reflection to set a private field value
     */
    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }

    /**
     * Use reflection to invoke the private buildFcmPayload method
     */
    private String invokeBuildFcmPayload(String fcmToken, String title, String body, Map<String, String> data)
            throws Exception {
        return invokeBuildFcmPayload(fcmToken, title, body, data, PushUrgency.HIGH, EPHEMERAL_TTL_SECONDS);
    }

    private String invokeBuildFcmPayload(String fcmToken, String title, String body, Map<String, String> data,
            PushUrgency urgency, Integer ttlSeconds) throws Exception {
        Method method = FcmHttpClient.class.getDeclaredMethod(
            "buildFcmPayload", String.class, String.class, String.class, Map.class, PushUrgency.class, Integer.class);
        method.setAccessible(true);
        return (String) method.invoke(fcmHttpClient, fcmToken, title, body, data, urgency, ttlSeconds);
    }
}









