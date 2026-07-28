package nl.vlettenrally.client;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.client.dto.TestConnectionResult;
import nl.vlettenrally.client.dto.legendstracking.EventDetailsResponse;
import nl.vlettenrally.client.dto.legendstracking.EventParticipant;
import nl.vlettenrally.client.dto.legendstracking.EventParticipantTrackResponse;
import nl.vlettenrally.client.dto.legendstracking.EventParticipantsLocationsResponse;
import nl.vlettenrally.client.exception.LocationApiException;
import nl.vlettenrally.observability.LocationMetrics;
import nl.vlettenrally.observability.LocationMetrics.ApiCaller;
import nl.vlettenrally.observability.LocationMetrics.ApiEndpoint;
import nl.vlettenrally.observability.LocationMetrics.ApiOutcome;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
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

    @Inject
    LocationMetrics metrics;

    public LocationApiClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public EventDetailsResponse fetchDetails(String baseUrl, ApiCaller caller) throws LocationApiException {
        return get(joinUrl(baseUrl, DETAILS_SUFFIX), EventDetailsResponse.class, ApiEndpoint.DETAILS, caller);
    }

    public EventParticipantsLocationsResponse fetchParticipantLocations(String baseUrl, ApiCaller caller)
            throws LocationApiException {
        return get(joinUrl(baseUrl, PARTICIPANT_LOCATIONS_SUFFIX), EventParticipantsLocationsResponse.class,
                ApiEndpoint.PARTICIPANTS_LOCATIONS, caller);
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
    public EventParticipantTrackResponse fetchParticipantTrack(String baseUrl, int eventParticipantId,
            ApiCaller caller) throws LocationApiException {
        return get(participantTrackUrl(baseUrl, eventParticipantId), EventParticipantTrackResponse.class,
                ApiEndpoint.PARTICIPANT_TRACK, caller);
    }

    /** Only ever the admin "test endpoint" button, so it supplies its own caller attribution. */
    public TestConnectionResult testConnection(String baseUrl) throws LocationApiException {
        EventDetailsResponse response = fetchDetails(baseUrl, ApiCaller.ADMIN_TEST);
        if (response.getEvent() == null) {
            throw new LocationApiException("Invalid response from /details: missing 'event'");
        }
        List<EventParticipant> participants = response.getEvent().getEventParticipants();
        int count = participants != null ? participants.size() : 0;
        return new TestConnectionResult(true, count, null);
    }

    /**
     * The single chokepoint every outbound call passes through, and therefore the only place that
     * records {@code location.api.duration}. Recording in the {@code finally} is what makes that
     * count trustworthy: every call lands on the histogram exactly once, whichever way it ended.
     */
    private <T> T get(String url, Class<T> type, ApiEndpoint endpoint, ApiCaller caller)
            throws LocationApiException {
        long startNanos = System.nanoTime();
        ApiOutcome outcome = ApiOutcome.OK;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                outcome = ApiOutcome.HTTP_ERROR;
                throw new LocationApiException(
                        "Failed to fetch " + url + ": HTTP " + response.statusCode());
            }

            return objectMapper.readValue(response.body(), type);

        } catch (LocationApiException e) {
            throw e;
        } catch (Exception e) {
            outcome = outcomeOf(e);
            throw new LocationApiException("Failed to fetch " + url + ": " + e.getMessage(), e);
        } finally {
            metrics.apiCall(endpoint, caller, outcome, System.nanoTime() - startNanos);
        }
    }

    /**
     * Classify a failed call by what we would do about it: give the provider time, fix our parsing,
     * or look at the network. A malformed URL never reaches the network but still lands on
     * {@code TRANSPORT_ERROR} — a degenerate case not worth its own value.
     */
    static ApiOutcome outcomeOf(Exception e) {
        if (e instanceof HttpTimeoutException) {
            // HttpConnectTimeoutException extends this, so both timeouts land here.
            return ApiOutcome.TIMEOUT;
        }
        if (e instanceof JacksonException) {
            return ApiOutcome.INVALID_BODY;
        }
        return ApiOutcome.TRANSPORT_ERROR;
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
