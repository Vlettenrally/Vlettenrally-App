package nl.vlettenrally.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.client.dto.EventsResponse;
import nl.vlettenrally.client.dto.ExternalEvent;
import nl.vlettenrally.client.dto.Registration;
import nl.vlettenrally.client.dto.RegistrationsResponse;
import nl.vlettenrally.client.exception.PreRegistrationApiException;
import nl.vlettenrally.config.PreRegistrationConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Client for the external pre-registration WordPress API.
 * 
 * Endpoints:
 * - GET /events - Get available events
 * - GET /registrations?event_ids={id} - Get team registrations for an event
 */
@ApplicationScoped
public class PreRegistrationApiClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    @Inject
    PreRegistrationConfig config;
    
    public PreRegistrationApiClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    
    /**
     * Fetch available events from the pre-registration API
     */
    public List<ExternalEvent> fetchEvents() throws PreRegistrationApiException {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl() + "/events"))
                    .timeout(Duration.ofSeconds(30))
                    .GET();
            
            // Add API key if configured
            config.apiKey()
                    .filter(key -> !key.isBlank())
                    .ifPresent(key -> requestBuilder.header("Authorization", "Bearer " + key));
            
            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            
            if (response.statusCode() != 200) {
                throw new PreRegistrationApiException(
                        "Failed to fetch events: HTTP " + response.statusCode()
                );
            }
            
            EventsResponse eventsResponse = objectMapper.readValue(
                    response.body(),
                    EventsResponse.class
            );
            
            return eventsResponse.getEvents() != null ? eventsResponse.getEvents() : List.of();
            
        } catch (PreRegistrationApiException e) {
            throw e;
        } catch (Exception e) {
            throw new PreRegistrationApiException("Failed to fetch events: " + e.getMessage(), e);
        }
    }
    
    /**
     * Fetch team registrations for a specific event
     */
    public List<Registration> fetchRegistrations(int eventId) throws PreRegistrationApiException {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl() + "/registrations?event_ids=" + eventId))
                    .timeout(Duration.ofSeconds(30))
                    .GET();
            
            // Add API key if configured
            config.apiKey()
                    .filter(key -> !key.isBlank())
                    .ifPresent(key -> requestBuilder.header("Authorization", "Bearer " + key));
            
            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            
            if (response.statusCode() != 200) {
                throw new PreRegistrationApiException(
                        "Failed to fetch registrations: HTTP " + response.statusCode()
                );
            }
            
            RegistrationsResponse registrationsResponse = objectMapper.readValue(
                    response.body(),
                    RegistrationsResponse.class
            );
            
            return registrationsResponse.getRegistrations() != null 
                    ? registrationsResponse.getRegistrations() 
                    : List.of();
            
        } catch (PreRegistrationApiException e) {
            throw e;
        } catch (Exception e) {
            throw new PreRegistrationApiException(
                    "Failed to fetch registrations: " + e.getMessage(), e
            );
        }
    }
    
    /**
     * Test connection to the pre-registration API
     */
    public boolean testConnection() {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl() + "/test"))
                    .timeout(Duration.ofSeconds(10))
                    .GET();
            
            config.apiKey()
                    .filter(key -> !key.isBlank())
                    .ifPresent(key -> requestBuilder.header("Authorization", "Bearer " + key));
            
            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
