package nl.vlettenrally.observability;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Metrics for location polling, fix ingestion and track backfill.
 * <p>
 * The vocabularies that mirror a domain enum ({@link FixOutcome}, {@link BackfillStatus}) are
 * mapped by constant name via {@code valueOf}. That is deliberate: adding a constant to the
 * domain enum without adding it here fails loudly on the first recording instead of silently
 * producing an unlabelled series.
 */
@Startup
@ApplicationScoped
public class LocationMetrics {

    private static final String SCOPE = "nl.vlettenrally.location";

    private static final AttributeKey<String> SOURCE = AttributeKey.stringKey("source");
    private static final AttributeKey<String> MODE = AttributeKey.stringKey("mode");
    private static final AttributeKey<String> TRIGGER = AttributeKey.stringKey("trigger");
    private static final AttributeKey<String> ENDPOINT = AttributeKey.stringKey("endpoint");
    private static final AttributeKey<String> CALLER = AttributeKey.stringKey("caller");

    /** Which Legendstracking endpoint an outbound call hit. */
    public enum ApiEndpoint {
        DETAILS,
        PARTICIPANTS_LOCATIONS,
        PARTICIPANT_TRACK
    }

    /**
     * Who asked for an outbound call. This is the dimension that separates the live poller's
     * steady drip from the far more expensive backfill fetches and the one-off admin calls —
     * {@code location.poll.sweep} cannot answer that, because most of its outcomes never reach
     * the network.
     */
    public enum ApiCaller {
        POLLER,
        BACKFILL_AUTO,
        BACKFILL_MANUAL,
        EVENT_IMPORT,
        ADMIN_TEST
    }

    /**
     * How an outbound call ended. The failure modes are split by what we would do about them:
     * {@code HTTP_ERROR} is the provider saying no, {@code TIMEOUT} is us giving up after 30s,
     * {@code TRANSPORT_ERROR} is the connection never working, {@code INVALID_BODY} is a 200 we
     * could not parse.
     */
    public enum ApiOutcome {
        OK,
        HTTP_ERROR,
        TIMEOUT,
        TRANSPORT_ERROR,
        INVALID_BODY
    }

    /** How one game's turn in a polling tick ended. */
    public enum SweepOutcome {
        OK,
        SKIPPED_INACTIVE,
        SKIPPED_FREQUENCY,
        NO_TRACKERS,
        API_ERROR,
        EMPTY_BODY,
        EXCEPTION
    }

    /** Which path a fix arrived on. */
    public enum FixSource {
        POLLER,
        TRACCAR
    }

    /**
     * Fate of a single fix. The {@code DROP_*} constants mirror
     * {@code TrackOutlierFilter.Decision}; the {@code INVALID_*} ones come from the cheap
     * validity check that runs before it.
     */
    public enum FixOutcome {
        ACCEPTED,
        INVALID_POINT,
        INVALID_LATLON,
        INVALID_TIME,
        DROP_DUPLICATE,
        DROP_ACCURACY,
        DROP_TELEPORT,
        DROP_SPIKE
    }

    /** How a checkpoint pass was detected. */
    public enum PassMode {
        SEGMENT,
        POINT
    }

    /** What started a backfill. */
    public enum BackfillTrigger {
        AUTO,
        MANUAL
    }

    /** Mirrors {@code LocationBackfillService.Status}, plus the uncaught-exception case. */
    public enum BackfillStatus {
        COMPLETED,
        COOLING_DOWN,
        BUSY,
        NO_PARTICIPANT,
        NOT_CONFIGURED,
        ERROR
    }

    /** Fate of a single point offered to the backfill persister. */
    public enum BackfillPointOutcome {
        INSERTED,
        DROPPED_GUARD,
        DROPPED_DUPLICATE,
        DROPPED_OUT_OF_WINDOW,
        DROPPED_INVALID
    }

    /** Result of a leader-election tick. Every scheduler is gated on this. */
    public enum LeaderOutcome {
        LEADER,
        FOLLOWER,
        DISABLED,
        ERROR
    }

    @Inject
    Meters meters;

    private DoubleHistogram apiDuration;
    private LongCounter pollSweep;
    private LongCounter fixIngest;
    private LongCounter checkpointPass;
    private LongCounter backfillRun;
    private LongCounter backfillPoint;
    private LongCounter leaderTick;

    @PostConstruct
    void init() {
        Meter meter = meters.meter(SCOPE);
        // Milliseconds, not seconds: the SDK's default bucket boundaries run 0..10000, which fits an
        // HTTP call with a 30s timeout well in ms and would collapse into the first bucket in s.
        // Overriding the boundaries would mean reaching for the alpha incubator API — not worth it.
        // The exported _count is the call rate, so this histogram replaces a separate counter.
        apiDuration = meter.histogramBuilder("location.api.duration")
                .setUnit("ms")
                .setDescription("Outbound Legendstracking calls, by endpoint, caller and outcome")
                .build();
        pollSweep = meter.counterBuilder("location.poll.sweep")
                .setUnit("{sweep}")
                .setDescription("Per-game polling attempts, by how they ended")
                .build();
        fixIngest = meter.counterBuilder("location.fix.ingest")
                .setUnit("{fix}")
                .setDescription("Location fixes offered to ingest, by source and fate")
                .build();
        checkpointPass = meter.counterBuilder("location.checkpoint.pass")
                .setUnit("{pass}")
                .setDescription("Checkpoint passes recorded, by detection mode")
                .build();
        backfillRun = meter.counterBuilder("location.backfill.run")
                .setUnit("{run}")
                .setDescription("Track backfill runs, by what started them and how they ended")
                .build();
        backfillPoint = meter.counterBuilder("location.backfill.point")
                .setUnit("{point}")
                .setDescription("Points offered to the backfill persister, by fate")
                .build();
        leaderTick = meter.counterBuilder("location.leader.tick")
                .setUnit("{tick}")
                .setDescription("Polling-leader election ticks, by result")
                .build();
    }

    /**
     * Record one outbound provider call. The caller passes nanos so no time unit leaks into
     * {@code LocationApiClient}. Unlike the count-carrying recordings below there is no zero guard:
     * a sub-millisecond call is a real observation, not noise.
     */
    public void apiCall(ApiEndpoint endpoint, ApiCaller caller, ApiOutcome outcome, long elapsedNanos) {
        apiDuration.record(elapsedNanos / 1_000_000.0,
                Attributes.of(ENDPOINT, Meters.tag(endpoint), CALLER, Meters.tag(caller),
                        Meters.OUTCOME, Meters.tag(outcome)));
    }

    public void sweep(SweepOutcome outcome) {
        pollSweep.add(1, Attributes.of(Meters.OUTCOME, Meters.tag(outcome)));
    }

    public void fix(FixSource source, FixOutcome outcome) {
        fixIngest.add(1, Attributes.of(SOURCE, Meters.tag(source), Meters.OUTCOME, Meters.tag(outcome)));
    }

    public void checkpointPass(PassMode mode, long count) {
        if (count > 0) {
            checkpointPass.add(count, Attributes.of(MODE, Meters.tag(mode)));
        }
    }

    public void backfillRun(BackfillTrigger trigger, BackfillStatus status) {
        backfillRun.add(1, Attributes.of(TRIGGER, Meters.tag(trigger), Meters.STATUS, Meters.tag(status)));
    }

    public void backfillPoints(BackfillPointOutcome outcome, long count) {
        if (count > 0) {
            backfillPoint.add(count, Attributes.of(Meters.OUTCOME, Meters.tag(outcome)));
        }
    }

    public void leaderTick(LeaderOutcome outcome) {
        leaderTick.add(1, Attributes.of(Meters.OUTCOME, Meters.tag(outcome)));
    }

    /**
     * Map a {@code TrackOutlierFilter.Decision} onto a {@link FixOutcome} by constant name.
     * {@code ACCEPT} maps to {@link FixOutcome#ACCEPTED}; everything else must match a
     * {@code DROP_*} constant.
     */
    public static FixOutcome fixOutcomeOf(Enum<?> decision) {
        if ("ACCEPT".equals(decision.name())) {
            return FixOutcome.ACCEPTED;
        }
        return FixOutcome.valueOf(decision.name());
    }

    /** Map a {@code LocationBackfillService.Status} onto a {@link BackfillStatus} by name. */
    public static BackfillStatus backfillStatusOf(Enum<?> status) {
        return BackfillStatus.valueOf(status.name());
    }

    /**
     * Map a backfill trigger onto the {@link ApiCaller} its provider calls are attributed to, so
     * {@code LocationBackfillService} only ever has to speak {@link BackfillTrigger}.
     */
    public static ApiCaller apiCallerOf(BackfillTrigger trigger) {
        return trigger == BackfillTrigger.AUTO ? ApiCaller.BACKFILL_AUTO : ApiCaller.BACKFILL_MANUAL;
    }
}
