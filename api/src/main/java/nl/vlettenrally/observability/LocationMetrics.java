package nl.vlettenrally.observability;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
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

    private LongCounter pollSweep;
    private LongCounter fixIngest;
    private LongCounter checkpointPass;
    private LongCounter backfillRun;
    private LongCounter backfillPoint;
    private LongCounter leaderTick;

    @PostConstruct
    void init() {
        Meter meter = meters.meter(SCOPE);
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
}
