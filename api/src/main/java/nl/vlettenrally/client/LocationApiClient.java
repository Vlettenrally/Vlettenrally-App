package nl.vlettenrally.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import nl.vlettenrally.client.dto.TestConnectionResult;
import nl.vlettenrally.client.dto.legendstracking.EventDetailsResponse;
import nl.vlettenrally.client.dto.legendstracking.EventParticipant;
import nl.vlettenrally.client.dto.legendstracking.EventParticipantsCheckpointsResponse;
import nl.vlettenrally.client.dto.legendstracking.EventParticipantsLocationsResponse;
import nl.vlettenrally.client.exception.LocationApiException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Client for the Legendstracking event API. Each method takes the event base URL
 * (e.g. {@code https://host/api/event/3265}) and appends the appropriate suffix.
 * The upstream caches each endpoint for 30 seconds.
 */
@ApplicationScoped
public class LocationApiClient {

    private static final String DETAILS_SUFFIX = "/details";
    private static final String PARTICIPANT_LOCATIONS_SUFFIX = "/participants_locations";
    private static final String PARTICIPANT_CHECKPOINTS_SUFFIX = "/participants_checkpoints";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LocationApiClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public EventDetailsResponse fetchDetails(String baseUrl) throws LocationApiException {
        return get(joinUrl(baseUrl, DETAILS_SUFFIX), EventDetailsResponse.class);
    }

    public EventParticipantsLocationsResponse fetchParticipantLocations(String baseUrl) throws LocationApiException {
        return get(joinUrl(baseUrl, PARTICIPANT_LOCATIONS_SUFFIX), EventParticipantsLocationsResponse.class);
    }

    public EventParticipantsCheckpointsResponse fetchParticipantCheckpoints(String baseUrl) throws LocationApiException {
        return get(joinUrl(baseUrl, PARTICIPANT_CHECKPOINTS_SUFFIX), EventParticipantsCheckpointsResponse.class);
    }

    public TestConnectionResult testConnection(String baseUrl) throws LocationApiException {
        EventDetailsResponse response = fetchDetails(baseUrl);
        if (response.getEvent() == null) {
            throw new LocationApiException("Invalid response from /details: missing 'event'");
        }
        List<EventParticipant> participants = response.getEvent().getEventParticipants();
        int count = participants != null ? participants.size() : 0;
        return new TestConnectionResult(true, count, null);
    }

    private <T> T get(String url, Class<T> type) throws LocationApiException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new LocationApiException(
                        "Failed to fetch " + url + ": HTTP " + response.statusCode());
            }

            return objectMapper.readValue(response.body(), type);

        } catch (LocationApiException e) {
            throw e;
        } catch (Exception e) {
            throw new LocationApiException("Failed to fetch " + url + ": " + e.getMessage(), e);
        }
    }

    private static String joinUrl(String baseUrl, String suffix) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return trimmed + suffix;
    }
}
