package nl.vlettenrally.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import nl.vlettenrally.data.enums.AutomationExecutionStatus;
import nl.vlettenrally.data.enums.AutomationTriggerType;
import nl.vlettenrally.data.enums.TransmissionChannel;
import nl.vlettenrally.observability.AutomationMetrics.Hook;
import nl.vlettenrally.observability.AutomationMetrics.SkipReason;
import nl.vlettenrally.observability.DeliveryMetrics.FcmCaller;
import nl.vlettenrally.observability.DeliveryMetrics.FcmOutcome;
import nl.vlettenrally.observability.DeliveryMetrics.RouteDecision;
import nl.vlettenrally.observability.DeliveryMetrics.SmsOutcome;
import nl.vlettenrally.observability.DeliveryMetrics.TransmitOutcome;
import nl.vlettenrally.observability.LocationMetrics.ApiCaller;
import nl.vlettenrally.observability.LocationMetrics.ApiEndpoint;
import nl.vlettenrally.observability.LocationMetrics.ApiOutcome;
import nl.vlettenrally.observability.LocationMetrics.BackfillPointOutcome;
import nl.vlettenrally.observability.LocationMetrics.BackfillStatus;
import nl.vlettenrally.observability.LocationMetrics.BackfillTrigger;
import nl.vlettenrally.observability.LocationMetrics.FixOutcome;
import nl.vlettenrally.observability.LocationMetrics.FixSource;
import nl.vlettenrally.observability.LocationMetrics.LeaderOutcome;
import nl.vlettenrally.observability.LocationMetrics.PassMode;
import nl.vlettenrally.observability.LocationMetrics.SweepOutcome;
import nl.vlettenrally.observability.WebSocketMetrics.Buffer;
import nl.vlettenrally.observability.WebSocketMetrics.CloseCause;
import nl.vlettenrally.observability.WebSocketMetrics.DeliverOutcome;
import nl.vlettenrally.observability.WebSocketMetrics.FlushOutcome;
import nl.vlettenrally.observability.WebSocketMetrics.OpenOutcome;
import nl.vlettenrally.observability.WebSocketMetrics.PublishFailure;
import nl.vlettenrally.observability.WebSocketMetrics.ResubscribeReason;
import nl.vlettenrally.observability.WebSocketMetrics.SessionKind;
import nl.vlettenrally.websockets.WebSocketSessionManager;

/**
 * Exercises every instrument in the catalogue against a real SDK meter provider and asserts the
 * exported names and units.
 * <p>
 * The point is to catch a typo'd metric name or a missing unit here rather than on a dashboard
 * that silently shows nothing — a renamed metric produces no error anywhere else in the stack.
 */
class MetricsCatalogTest {

    private RecordingMetricReader reader;
    private Meters meters;

    @BeforeEach
    void setUp() {
        reader = new RecordingMetricReader();
        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .registerMetricReader(reader)
                .build();
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setMeterProvider(meterProvider)
                .build();

        meters = new Meters();
        meters.openTelemetry = openTelemetry;
    }

    @Test
    void automationInstrumentsExportTheirCatalogueNames() {
        AutomationMetrics metrics = new AutomationMetrics();
        metrics.meters = meters;
        metrics.init();

        metrics.fired(AutomationTriggerType.CHECKPOINT_ENTRY, AutomationExecutionStatus.SUCCESS);
        metrics.skipped(SkipReason.COOLDOWN);
        metrics.actionFailed("AwardPoints");
        metrics.hookFailed(Hook.DISTANCE);

        assertEquals(
                Set.of("automation.fire", "automation.skip", "automation.action.error", "automation.hook.error"),
                names());
    }

    @Test
    void locationInstrumentsExportTheirCatalogueNames() {
        LocationMetrics metrics = new LocationMetrics();
        metrics.meters = meters;
        metrics.init();

        metrics.apiCall(ApiEndpoint.PARTICIPANTS_LOCATIONS, ApiCaller.POLLER, ApiOutcome.OK, 12_000_000L);
        metrics.sweep(SweepOutcome.OK);
        metrics.fix(FixSource.POLLER, FixOutcome.ACCEPTED);
        metrics.checkpointPass(PassMode.SEGMENT, 1);
        metrics.backfillRun(BackfillTrigger.AUTO, BackfillStatus.COMPLETED);
        metrics.backfillPoints(BackfillPointOutcome.DROPPED_DUPLICATE, 3);
        metrics.leaderTick(LeaderOutcome.LEADER);

        assertEquals(
                Set.of("location.api.duration", "location.poll.sweep", "location.fix.ingest",
                        "location.checkpoint.pass", "location.backfill.run", "location.backfill.point",
                        "location.leader.tick"),
                names());
    }

    @Test
    void webSocketInstrumentsExportTheirCatalogueNames() {
        WebSocketMetrics metrics = new WebSocketMetrics();
        metrics.meters = meters;
        metrics.sessionManager = new WebSocketSessionManager();
        metrics.init();

        metrics.published();
        metrics.publishFailed(PublishFailure.POOL_EXHAUSTED);
        metrics.delivered(DeliverOutcome.SENT);
        metrics.sessionOpened(SessionKind.TEAM, OpenOutcome.OK);
        metrics.sessionClosed(CloseCause.CLOSE);
        metrics.resubscribed(ResubscribeReason.ENDED);
        metrics.flushed(Buffer.LOCATION, FlushOutcome.SENT);

        // ws.sessions.active is an observable gauge: it only appears because collect() below
        // runs its callback, which is exactly the behaviour worth pinning down.
        assertEquals(
                Set.of("ws.publish.ok", "ws.publish.failed", "ws.deliver", "ws.sessions.active",
                        "ws.session.opened", "ws.session.closed", "ws.resubscribe", "ws.broadcast.flush"),
                names());
    }

    @Test
    void deliveryInstrumentsExportTheirCatalogueNames() {
        DeliveryMetrics metrics = new DeliveryMetrics();
        metrics.meters = meters;
        metrics.init();

        metrics.routed(TransmissionChannel.SMS, RouteDecision.GATED);
        metrics.transmitted(TransmissionChannel.PUSH, TransmitOutcome.SUCCESS);
        metrics.fcmRequest(FcmOutcome.UNREGISTERED);
        metrics.fcmFanout(FcmCaller.TEAM_MESSAGE, 2);
        metrics.smsSent(SmsOutcome.QUEUED);

        assertEquals(Set.of("msg.route", "msg.transmit", "fcm.request", "fcm.fanout", "sms.send"), names());
    }

    @Test
    void everyInstrumentDeclaresAUnit() {
        AutomationMetrics automation = new AutomationMetrics();
        automation.meters = meters;
        automation.init();
        automation.fired(AutomationTriggerType.SPEED_ALERT, AutomationExecutionStatus.FAILED);

        LocationMetrics location = new LocationMetrics();
        location.meters = meters;
        location.init();
        location.sweep(SweepOutcome.API_ERROR);

        DeliveryMetrics delivery = new DeliveryMetrics();
        delivery.meters = meters;
        delivery.init();
        delivery.smsSent(SmsOutcome.FAILED);

        Collection<MetricData> collected = collect();
        assertFalse(collected.isEmpty(), "expected at least one exported metric");
        for (MetricData metric : collected) {
            assertFalse(metric.getUnit() == null || metric.getUnit().isBlank(),
                    "missing unit for " + metric.getName());
            assertFalse(metric.getDescription() == null || metric.getDescription().isBlank(),
                    "missing description for " + metric.getName());
        }
    }

    /** Counts that arrive as zero must not create a series — they would be pure noise. */
    @Test
    void zeroCountsAreNotRecorded() {
        LocationMetrics metrics = new LocationMetrics();
        metrics.meters = meters;
        metrics.init();

        metrics.checkpointPass(PassMode.POINT, 0);
        metrics.backfillPoints(BackfillPointOutcome.INSERTED, 0);

        assertTrue(names().isEmpty(), "zero-valued recordings should not create a series");
    }

    private Collection<MetricData> collect() {
        return reader.collect();
    }

    private Set<String> names() {
        return collect().stream().map(MetricData::getName).collect(Collectors.toSet());
    }
}
