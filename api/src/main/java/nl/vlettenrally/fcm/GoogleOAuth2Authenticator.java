package nl.vlettenrally.fcm;

import java.io.FileInputStream;
import java.io.IOException;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;

import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Authenticator for Google Cloud services using OAuth2 service account credentials.
 * 
 * This class uses the Google Auth Library to handle:
 * - Parsing service account JSON credentials
 * - Generating and refreshing OAuth2 access tokens automatically
 * - Token caching with automatic refresh
 */
@ApplicationScoped
public class GoogleOAuth2Authenticator {
    
    private static final String FIREBASE_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    
    @ConfigProperty(name = "vlettenrally.firebase.credentials-path", defaultValue = "")
    String credentialsPath;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private GoogleCredentials credentials;
    private String projectId;
    private boolean initialized = false;
    
    @PostConstruct
    void init() {
        if (credentialsPath == null || credentialsPath.isBlank()) {
            Log.warn("Firebase credentials path not configured");
            return;
        }
        
        try {
            loadCredentials();
            initialized = true;
            Log.info("Google OAuth2 authenticator initialized successfully");
        } catch (Exception e) {
            Log.error("Failed to initialize Google OAuth2 authenticator", e);
        }
    }
    
    /**
     * Load and configure Google credentials from service account JSON file.
     */
    private void loadCredentials() throws IOException {
        try (FileInputStream fis = new FileInputStream(credentialsPath)) {
            // Parse the JSON to extract project ID
            fis.mark(Integer.MAX_VALUE);
            JsonNode root = objectMapper.readTree(fis);
            projectId = root.get("project_id").asText();
            
            // Reset stream and load credentials
            fis.reset();
            credentials = GoogleCredentials.fromStream(fis)
                    .createScoped(FIREBASE_SCOPE);
            
            Log.infof("Loaded service account credentials (project: %s)", projectId);
        }
    }
    
    /**
     * Get a valid access token, automatically refreshing if necessary.
     * The Google Auth library handles caching and refresh automatically.
     */
    public String getAccessToken() throws IOException {
        if (!initialized) {
            throw new IOException("OAuth2 authenticator not initialized");
        }
        
        // Refresh token if needed (library handles caching automatically)
        credentials.refreshIfExpired();
        
        return credentials.getAccessToken().getTokenValue();
    }
    
    /**
     * Get the Firebase project ID from the service account credentials.
     */
    public String getProjectId() {
        return projectId;
    }
    
    /**
     * Check if the authenticator is properly initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }
}
