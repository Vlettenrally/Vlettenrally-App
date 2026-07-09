package nl.vlettenrally.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import nl.vlettenrally.client.dto.TestConnectionResult;
import nl.vlettenrally.client.dto.legendstracking.EventDetailsResponse;
import nl.vlettenrally.client.dto.legendstracking.EventParticipant;
import nl.vlettenrally.client.dto.legendstracking.EventParticipantTrackResponse;
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
    private static final String PARTICIPANT_TRACK_SUFFIX = "/participant_track";

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

    /**
     * Fetch a single participant's full historical track for backfilling.
     * <p>
     * The backfill endpoint is keyed by the <em>event participant</em> id, not the event id:
     * {@code https://host/api/event/{eventParticipantId}/participant_track}. The game only stores
     * the event base URL ({@code https://host/api/event/{eventId}}), so the trailing event-id
     * segment is stripped to recover the {@code .../api/event} root before appending the
     * participant id and suffix.
     */
    public EventParticipantTrackResponse fetchParticipantTrack(String baseUrl, int eventParticipantId)
            throws LocationApiException {
        return get(participantTrackUrl(baseUrl, eventParticipantId), EventParticipantTrackResponse.class);
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

    /**
     * Derive {@code .../api/event/{eventParticipantId}/participant_track} from the event base URL
     * by dropping its trailing event-id segment.
     */
    static String participantTrackUrl(String baseUrl, int eventParticipantId) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        int lastSlash = trimmed.lastIndexOf('/');
        if (lastSlash <= 0) {
            throw new IllegalArgumentException("Cannot derive participant_track URL from: " + baseUrl);
        }
        String root = trimmed.substring(0, lastSlash);
        return root + "/" + eventParticipantId + PARTICIPANT_TRACK_SUFFIX;
    }
}
