package nl.vlettenrally.observability;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import com.twilio.rest.api.v2010.account.Message.Status;

import nl.vlettenrally.data.enums.AutomationExecutionStatus;
import nl.vlettenrally.data.enums.AutomationTriggerType;
import nl.vlettenrally.data.enums.TransmissionChannel;
import nl.vlettenrally.fcm.FcmHttpClient;
import nl.vlettenrally.observability.DeliveryMetrics.FcmOutcome;
import nl.vlettenrally.observability.DeliveryMetrics.SmsOutcome;
import nl.vlettenrally.provider.TwilioSmsTransmitter;
import nl.vlettenrally.service.LocationBackfillService;
import nl.vlettenrally.service.util.TrackOutlierFilter;

/**
 * Guards the mappings from domain enums onto metric attribute values.
 * <p>
 * The failure this prevents is quiet: someone adds a constant to a domain enum, the mapper falls
 * through or throws inside a metric call, and either a series goes unlabelled or an instrumented
 * code path starts throwing. Driving every constant through the mapper turns that into a test
 * failure at the moment the enum grows.
 */
class AttributeVocabularyTest {

    @Test
    void everyTrackOutlierDecisionMapsToAFixOutcome() {
        for (TrackOutlierFilter.Decision decision : TrackOutlierFilter.Decision.values()) {
            assertDoesNotThrow(() -> LocationMetrics.fixOutcomeOf(decision),
                    "no FixOutcome for decision " + decision);
        }
        assertEquals(LocationMetrics.FixOutcome.ACCEPTED,
                LocationMetrics.fixOutcomeOf(TrackOutlierFilter.Decision.ACCEPT));
        assertEquals(LocationMetrics.FixOutcome.DROP_TELEPORT,
                LocationMetrics.fixOutcomeOf(TrackOutlierFilter.Decision.DROP_TELEPORT));
    }

    @Test
    void everyBackfillStatusMapsToAMetricStatus() {
        for (LocationBackfillService.Status status : LocationBackfillService.Status.values()) {
            assertDoesNotThrow(() -> LocationMetrics.backfillStatusOf(status),
                    "no BackfillStatus for " + status);
        }
        // ERROR has no domain counterpart: it is recorded only when a run throws.
        assertEquals(LocationBackfillService.Status.values().length + 1,
                LocationMetrics.BackfillStatus.values().length);
    }

    @Test
    void everyTwilioStatusMapsToAnSmsOutcome() {
        for (Status status : Status.values()) {
            assertNotNull(TwilioSmsTransmitter.outcomeOf(status), "no SmsOutcome for " + status);
        }
        assertEquals(SmsOutcome.OTHER, TwilioSmsTransmitter.outcomeOf(null));
        assertEquals(SmsOutcome.UNDELIVERED, TwilioSmsTransmitter.outcomeOf(Status.UNDELIVERED));
    }

    @Test
    void fcmFailuresAreClassifiedByErrorCodeThenStatus() {
        assertEquals(FcmOutcome.UNREGISTERED, FcmHttpClient.classify(404, "UNREGISTERED"));
        assertEquals(FcmOutcome.RATE_LIMITED, FcmHttpClient.classify(429, "QUOTA_EXCEEDED"));
        assertEquals(FcmOutcome.UNAVAILABLE, FcmHttpClient.classify(503, "UNAVAILABLE"));
        assertEquals(FcmOutcome.AUTH_ERROR, FcmHttpClient.classify(401, "THIRD_PARTY_AUTH_ERROR"));

        // No structured code: fall back to the HTTP status rather than collapsing to OTHER.
        assertEquals(FcmOutcome.RATE_LIMITED, FcmHttpClient.classify(429, null));
        assertEquals(FcmOutcome.AUTH_ERROR, FcmHttpClient.classify(403, null));
        assertEquals(FcmOutcome.UNAVAILABLE, FcmHttpClient.classify(500, null));

        // Neither recognised — the honest answer, not a guess.
        assertEquals(FcmOutcome.OTHER, FcmHttpClient.classify(418, "TEAPOT"));
    }

    @Test
    void domainEnumsRenderAsLowercaseAttributeValues() {
        assertEquals("checkpoint_entry", Meters.tag(AutomationTriggerType.CHECKPOINT_ENTRY));
        assertEquals("partial", Meters.tag(AutomationExecutionStatus.PARTIAL));
        assertEquals("websocket", Meters.tag(TransmissionChannel.WEBSOCKET));

        // No constant may render blank or carry whitespace.
        for (AutomationTriggerType trigger : AutomationTriggerType.values()) {
            String tag = Meters.tag(trigger);
            assertFalse(tag.isBlank(), "blank tag for " + trigger);
            assertFalse(tag.contains(" "), "whitespace in tag for " + trigger);
        }
    }
}
