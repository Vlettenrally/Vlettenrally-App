package nl.vlettenrally.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.vlettenrally.client.dto.legendstracking.EventParticipantLocation;
import nl.vlettenrally.client.dto.legendstracking.EventParticipantTrackResponse;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the pure bits of {@link LocationApiClient}: deriving the participant_track URL from
 * the event base URL, and deserialising the participant_track response shape.
 */
class LocationApiClientTest {

    @Test
    void participantTrackUrl_replacesEventIdSegmentWithParticipantIdAndSuffix() {
        assertEquals(
            "https://host/api/event/82792/participant_track",
            LocationApiClient.participantTrackUrl("https://host/api/event/3265", 82792));
    }

    @Test
    void participantTrackUrl_toleratesTrailingSlash() {
        assertEquals(
            "https://host/api/event/82792/participant_track",
            LocationApiClient.participantTrackUrl("https://host/api/event/3265/", 82792));
    }

    @Test
    void participantTrackResponse_deserialisesPointsWithSharedShape() throws Exception {
        String json = """
            {
              "participantTrack": {
                "participantDetails": [
                  {"first_name": "", "last_name": "20432", "country": "NL", "bib": 20432, "status_id": 1001}
                ],
                "participantTrack": [
                  {
                    "event_participants_id": 82792,
                    "tracker_id": 20432,
                    "location_time": "2026-06-14T12:44:22.000Z",
                    "report_time": "2026-06-14T12:44:44.000Z",
                    "location": {"x": 4.714143, "y": 52.015766},
                    "battery_percentage": 100,
                    "is_buffered": 1,
                    "gps_accuracy": 2,
                    "speed": -1
                  }
                ]
              }
            }
            """;
        ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        EventParticipantTrackResponse response = mapper.readValue(json, EventParticipantTrackResponse.class);

        assertNotNull(response.getParticipantTrack());
        assertEquals(1, response.getParticipantTrack().getParticipantTrack().size());
        EventParticipantLocation point = response.getParticipantTrack().getParticipantTrack().getFirst();
        assertEquals(82792, point.getEventParticipantsId());
        assertEquals(20432, point.getTrackerId());
        assertEquals("2026-06-14T12:44:22.000Z", point.getLocationTime());
        assertEquals(4.714143, point.getLocation().getLongitude(), 1e-9);
        assertEquals(52.015766, point.getLocation().getLatitude(), 1e-9);
    }
}
