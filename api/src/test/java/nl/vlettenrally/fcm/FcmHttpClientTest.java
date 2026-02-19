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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    private FcmHttpClient fcmHttpClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        fcmHttpClient = new FcmHttpClient();

        // Use reflection to inject mocks and set up the client
        setField(fcmHttpClient, "enabled", true);
        setField(fcmHttpClient, "authenticator", authenticator);
        setField(fcmHttpClient, "httpClient", httpClient);
        setField(fcmHttpClient, "objectMapper", objectMapper);
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

        // Verify data has exactly 5 fields (title, body, messageId, messageType, teamId)
        assertEquals(5, dataNode.size());
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
        // Should only have title and body when additional data is null
        assertEquals(2, dataNode.size());
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
        // Should only have title and body when additional data is empty
        assertEquals(2, dataNode.size());
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
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        // Act
        String messageId = fcmHttpClient.sendNotification(fcmToken, title, body, data);

        // Assert
        assertEquals("projects/mock-project-id/messages/msg-12345", messageId);
        verify(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void testSendNotification_FirebaseDisabled() {
        // Arrange
        setField(fcmHttpClient, "enabled", false);

        // Act & Assert
        FcmHttpClient.FcmException exception = assertThrows(
            FcmHttpClient.FcmException.class,
            () -> fcmHttpClient.sendNotification("token", "title", "body", null)
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
            () -> fcmHttpClient.sendNotification("token", "title", "body", null)
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
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        // Act & Assert
        FcmHttpClient.FcmException exception = assertThrows(
            FcmHttpClient.FcmException.class,
            () -> fcmHttpClient.sendNotification("token", "title", "body", null)
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

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Network error"));

        // Act & Assert
        FcmHttpClient.FcmException exception = assertThrows(
            FcmHttpClient.FcmException.class,
            () -> fcmHttpClient.sendNotification("token", "title", "body", null)
        );
        assertTrue(exception.getMessage().contains("Network error"));
    }

    @Test
    void testSendNotification_InterruptedException() throws Exception {
        // Arrange
        when(authenticator.isInitialized()).thenReturn(true);
        when(authenticator.getAccessToken()).thenReturn("mock-access-token");
        when(authenticator.getProjectId()).thenReturn("mock-project-id");

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("Thread interrupted"));

        // Act & Assert
        FcmHttpClient.FcmException exception = assertThrows(
            FcmHttpClient.FcmException.class,
            () -> fcmHttpClient.sendNotification("token", "title", "body", null)
        );
        assertTrue(exception.getMessage().contains("interrupted"));
        assertTrue(Thread.currentThread().isInterrupted());
        // Clear interrupt flag for other tests
        Thread.interrupted();
    }

    // Helper methods

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
        Method method = FcmHttpClient.class.getDeclaredMethod(
            "buildFcmPayload", String.class, String.class, String.class, Map.class);
        method.setAccessible(true);
        return (String) method.invoke(fcmHttpClient, fcmToken, title, body, data);
    }
}









