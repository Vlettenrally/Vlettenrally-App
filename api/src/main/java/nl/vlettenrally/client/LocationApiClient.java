package nl.vlettenrally.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import nl.vlettenrally.client.dto.LocationResponse;
import nl.vlettenrally.client.dto.ParticipantLocation;
import nl.vlettenrally.client.dto.TestConnectionResult;
import nl.vlettenrally.client.exception.LocationApiException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Client for fetching location data from external tracking APIs.
 * 
 * The location API returns data in the Socialrun format with participant locations.
 */
@ApplicationScoped
public class LocationApiClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    public LocationApiClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    
    /**
     * Fetch location data from the given URL
     */
    public LocationResponse fetchLocations(String url) throws LocationApiException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
            
            if (response.statusCode() != 200) {
                throw new LocationApiException(
                        "Failed to fetch locations: HTTP " + response.statusCode()
                );
            }
            
            return objectMapper.readValue(response.body(), LocationResponse.class);
            
        } catch (LocationApiException e) {
            throw e;
        } catch (Exception e) {
            throw new LocationApiException("Failed to fetch locations: " + e.getMessage(), e);
        }
    }
    
    /**
     * Test connection to a location endpoint
     * Returns the number of participants found, or throws exception on failure
     */
    public TestConnectionResult testConnection(String url) throws LocationApiException {
        LocationResponse response = fetchLocations(url);
        
        if (response.getSocialrunParticipantsLocations() == null) {
            throw new LocationApiException("Invalid response format: missing socialrunParticipantsLocations");
        }
        
        List<ParticipantLocation> locations = response.getSocialrunParticipantsLocations().getParticipantLocations();
        int participantCount = locations != null ? locations.size() : 0;
        
        return new TestConnectionResult(true, participantCount, null);
    }
}
