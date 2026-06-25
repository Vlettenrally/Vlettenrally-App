package nl.vlettenrally.api.v1;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.data.dto.device.FcmTokenRequest;
import nl.vlettenrally.service.FcmTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.UUID;

/**
 * Unit tests for FcmTokenResource.
 * Tests HTTP endpoints for FCM token registration.
 */
@ExtendWith(MockitoExtension.class)
class FcmTokenResourceTest {

    @Mock
    FcmTokenService fcmTokenService;

    @Mock
    SecurityContext securityContext;

    @Mock
    Principal principal;

    @InjectMocks
    FcmTokenResource fcmTokenResource;

    private UUID userId;
    private String fcmToken;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        fcmToken = "esBvK0B6vAw:APA91bF1qX_example";
    }

    // Helper method for tests that need security context
    private void setupSecurityContext() {
        when(securityContext.getUserPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn(userId.toString());
    }

    @Test
    void registerFcmToken_teamUserValidInput_returnsOk() {
        // Arrange
        setupSecurityContext();
        FcmTokenRequest request = new FcmTokenRequest(fcmToken);
        when(securityContext.isUserInRole(nl.vlettenrally.api.security.SecurityRoles.STAFF)).thenReturn(false);
        when(fcmTokenService.registerToken(userId, false, fcmToken, null, null)).thenReturn(UUID.randomUUID());

        // Act & Assert
        try (Response response = fcmTokenResource.registerFcmToken(request, securityContext)) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            verify(fcmTokenService).registerToken(userId, false, fcmToken, null, null);
        }
    }

    @Test
    void registerFcmToken_adminUserValidInput_returnsOk() {
        // Arrange
        setupSecurityContext();
        FcmTokenRequest request = new FcmTokenRequest(fcmToken);
        when(securityContext.isUserInRole(nl.vlettenrally.api.security.SecurityRoles.STAFF)).thenReturn(true);
        when(fcmTokenService.registerToken(userId, true, fcmToken, null, null)).thenReturn(UUID.randomUUID());

        // Act & Assert
        try (Response response = fcmTokenResource.registerFcmToken(request, securityContext)) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            verify(fcmTokenService).registerToken(userId, true, fcmToken, null, null);
        }
    }

    @Test
    void registerFcmToken_nullRequest_returnsBadRequest() {
        // Act & Assert
        try (Response response = fcmTokenResource.registerFcmToken(null, securityContext)) {
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
            ErrorResponse error = (ErrorResponse) response.getEntity();
            assertTrue(error.message().contains("required"));
        }
    }

    @Test
    void registerFcmToken_nullToken_returnsBadRequest() {
        // Arrange
        FcmTokenRequest request = new FcmTokenRequest(null);

        // Act & Assert
        try (Response response = fcmTokenResource.registerFcmToken(request, securityContext)) {
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        }
    }

    @Test
    void registerFcmToken_blankToken_returnsBadRequest() {
        // Arrange
        FcmTokenRequest request = new FcmTokenRequest("   ");

        // Act & Assert
        try (Response response = fcmTokenResource.registerFcmToken(request, securityContext)) {
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        }
    }

    @Test
    void registerFcmToken_invalidUserId_returnsUnauthorized() {
        // Arrange
        FcmTokenRequest request = new FcmTokenRequest(fcmToken);
        when(securityContext.getUserPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn("not-a-uuid");

        // Act & Assert
        try (Response response = fcmTokenResource.registerFcmToken(request, securityContext)) {
            assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
        }
    }

    @Test
    void registerFcmToken_serviceException_returnsInternalServerError() {
        // Arrange
        setupSecurityContext();
        FcmTokenRequest request = new FcmTokenRequest(fcmToken);
        when(securityContext.isUserInRole(nl.vlettenrally.api.security.SecurityRoles.STAFF)).thenReturn(false);
        when(fcmTokenService.registerToken(any(), anyBoolean(), anyString(), any(), any())).thenThrow(new RuntimeException("DB error"));

        // Act & Assert
        try (Response response = fcmTokenResource.registerFcmToken(request, securityContext)) {
            assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        }
    }

    @Test
    void unregisterFcmToken_teamUserValidInput_returnsOk() {
        // Arrange
        setupSecurityContext();
        when(securityContext.isUserInRole(nl.vlettenrally.api.security.SecurityRoles.STAFF)).thenReturn(false);
        doNothing().when(fcmTokenService).unregisterToken(userId, false);

        // Act & Assert
        try (Response response = fcmTokenResource.unregisterFcmToken(fcmToken, securityContext)) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            verify(fcmTokenService).unregisterToken(userId, false);
        }
    }

    @Test
    void unregisterFcmToken_adminUserValidInput_returnsOk() {
        // Arrange
        setupSecurityContext();
        when(securityContext.isUserInRole(nl.vlettenrally.api.security.SecurityRoles.STAFF)).thenReturn(true);
        doNothing().when(fcmTokenService).unregisterToken(userId, true);

        // Act & Assert
        try (Response response = fcmTokenResource.unregisterFcmToken(fcmToken, securityContext)) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            verify(fcmTokenService).unregisterToken(userId, true);
        }
    }

    @Test
    void unregisterFcmToken_nullToken_returnsBadRequest() {
        // Act & Assert
        try (Response response = fcmTokenResource.unregisterFcmToken(null, securityContext)) {
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        }
    }

    @Test
    void unregisterFcmToken_blankToken_returnsBadRequest() {
        // Act & Assert
        try (Response response = fcmTokenResource.unregisterFcmToken("   ", securityContext)) {
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        }
    }

    @Test
    void unregisterFcmToken_invalidUserId_returnsUnauthorized() {
        // Arrange
        when(securityContext.getUserPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn("not-a-uuid");

        // Act & Assert
        try (Response response = fcmTokenResource.unregisterFcmToken(fcmToken, securityContext)) {
            assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
        }
    }

    @Test
    void unregisterFcmToken_serviceException_returnsInternalServerError() {
        // Arrange
        setupSecurityContext();
        when(securityContext.isUserInRole(nl.vlettenrally.api.security.SecurityRoles.STAFF)).thenReturn(false);
        doThrow(new RuntimeException("DB error")).when(fcmTokenService).unregisterToken(any(), anyBoolean());

        // Act & Assert
        try (Response response = fcmTokenResource.unregisterFcmToken(fcmToken, securityContext)) {
            assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        }
    }
}














