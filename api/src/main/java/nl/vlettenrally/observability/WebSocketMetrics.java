package nl.vlettenrally.observability;

import java.util.EnumMap;
import java.util.Map;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongGauge;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.websockets.WebSocketSessionManager;

/**
 * Metrics for WebSocket sessions and cross-pod message distribution.
 * <p>
 * {@code ws.publish.ok} and {@code ws.publish.failed} predate this class; their names and
 * attribute values are kept exactly as they were so existing dashboards keep working.
 */
@Startup
@ApplicationScoped
public class WebSocketMetrics {

    private static final String SCOPE = "nl.vlettenrally.websockets";

    private static final AttributeKey<String> KIND = AttributeKey.stringKey("kind");
    private static final AttributeKey<String> CAUSE = AttributeKey.stringKey("cause");
    private static final AttributeKey<String> BUFFER = AttributeKey.stringKey("buffer");

    /** Why a publish to Valkey was dropped. */
    public enum PublishFailure {
        POOL_EXHAUSTED,
        OTHER,
        SERIALIZE
    }

    /** Fate of a single frame handed to a local session. */
    public enum DeliverOutcome {
        SENT,
        SESSION_CLOSED,
        SEND_ERROR
    }

    public enum SessionKind {
        TEAM,
        ADMIN
    }

    /** How a connection attempt ended. */
    public enum OpenOutcome {
        OK,
        INVALID_TOKEN,
        VALIDATION_ERROR
    }

    /** Which lifecycle callback tore the session down. */
    public enum CloseCause {
        CLOSE,
        ERROR
    }

    /** Why the Valkey subscription had to be re-established. */
    public enum ResubscribeReason {
        ENDED,
        ERROR,
        SUBSCRIBE_FAILED
    }

    public enum Buffer {
        LOCATION,
        PHOTO,
        STANDINGS
    }

    /** Whether a buffered batch reached anyone. */
    public enum FlushOutcome {
        SENT,
        DROPPED
    }

    @Inject
    Meters meters;

    @Inject
    WebSocketSessionManager sessionManager;

    private LongCounter publishOk;
    private LongCounter publishFailed;
    private LongCounter deliver;
    private LongCounter sessionOpened;
    private LongCounter sessionClosed;
    private LongCounter resubscribe;
    private LongCounter broadcastFlush;

    /**
     * Held in a field: {@code buildWithCallback} returns a registration the SDK may only hold
     * weakly, so dropping it would silently stop the gauge reporting.
     */
    @SuppressWarnings("unused")
    private ObservableLongGauge activeSessions;

    /** sendToSession runs once per frame per session, so its attributes are pre-allocated. */
    private final Map<DeliverOutcome, Attributes> deliverAttributes = new EnumMap<>(DeliverOutcome.class);
    private final Map<PublishFailure, Attributes> publishFailureAttributes = new EnumMap<>(PublishFailure.class);
    private Attributes teamKind;
    private Attributes adminKind;

    @PostConstruct
    void init() {
        for (DeliverOutcome outcome : DeliverOutcome.values()) {
            deliverAttributes.put(outcome, Attributes.of(Meters.OUTCOME, Meters.tag(outcome)));
        }
        for (PublishFailure reason : PublishFailure.values()) {
            publishFailureAttributes.put(reason, Attributes.of(Meters.REASON, Meters.tag(reason)));
        }
        teamKind = Attributes.of(KIND, Meters.tag(SessionKind.TEAM));
        adminKind = Attributes.of(KIND, Meters.tag(SessionKind.ADMIN));

        Meter meter = meters.meter(SCOPE);
        publishOk = meter.counterBuilder("ws.publish.ok")
                .setUnit("{message}")
                .setDescription("WebSocket messages successfully published to Valkey")
                .build();
        publishFailed = meter.counterBuilder("ws.publish.failed")
                .setUnit("{message}")
                .setDescription("WebSocket messages dropped because publishing to Valkey failed")
                .build();
        deliver = meter.counterBuilder("ws.deliver")
                .setUnit("{frame}")
                .setDescription("Frames handed to a local WebSocket session, by outcome")
                .build();
        sessionOpened = meter.counterBuilder("ws.session.opened")
                .setUnit("{session}")
                .setDescription("WebSocket connection attempts, by session kind and outcome")
                .build();
        sessionClosed = meter.counterBuilder("ws.session.closed")
                .setUnit("{session}")
                .setDescription("WebSocket sessions torn down, by what triggered the teardown")
                .build();
        resubscribe = meter.counterBuilder("ws.resubscribe")
                .setUnit("{attempt}")
                .setDescription("Attempts to re-establish the Valkey pub/sub subscription")
                .build();
        broadcastFlush = meter.counterBuilder("ws.broadcast.flush")
                .setUnit("{batch}")
                .setDescription("Buffered broadcast batches flushed, by buffer and outcome")
                .build();

        // Runs on the metric-reader thread every export interval, so it must stay cheap:
        // an in-process ConcurrentHashMap walk only, never Valkey and never the database.
        activeSessions = meter.gaugeBuilder("ws.sessions.active").ofLongs()
                .setUnit("{session}")
                .setDescription("WebSocket sessions currently held by this instance")
                .buildWithCallback(observer -> {
                    long team = 0;
                    long admin = 0;
                    for (var session : sessionManager.getAllSessions()) {
                        if (session.isTeam()) {
                            team++;
                        } else {
                            admin++;
                        }
                    }
                    observer.record(team, teamKind);
                    observer.record(admin, adminKind);
                });
    }

    public void published() {
        publishOk.add(1);
    }

    public void publishFailed(PublishFailure reason) {
        publishFailed.add(1, publishFailureAttributes.get(reason));
    }

    public void delivered(DeliverOutcome outcome) {
        deliver.add(1, deliverAttributes.get(outcome));
    }

    public void sessionOpened(SessionKind kind, OpenOutcome outcome) {
        sessionOpened.add(1, Attributes.of(KIND, Meters.tag(kind), Meters.OUTCOME, Meters.tag(outcome)));
    }

    public void sessionClosed(CloseCause cause) {
        sessionClosed.add(1, Attributes.of(CAUSE, Meters.tag(cause)));
    }

    public void resubscribed(ResubscribeReason reason) {
        resubscribe.add(1, Attributes.of(Meters.REASON, Meters.tag(reason)));
    }

    public void flushed(Buffer buffer, FlushOutcome outcome) {
        broadcastFlush.add(1, Attributes.of(BUFFER, Meters.tag(buffer), Meters.OUTCOME, Meters.tag(outcome)));
    }
}
