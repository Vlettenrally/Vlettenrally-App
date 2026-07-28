package nl.vlettenrally.observability;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.enums.TransmissionChannel;

/**
 * Metrics for the message delivery pipeline: the channel decision in MessageService, and the
 * Firebase and Twilio transports underneath it.
 */
@Startup
@ApplicationScoped
public class DeliveryMetrics {

    private static final String SCOPE = "nl.vlettenrally.delivery";

    private static final AttributeKey<String> CHANNEL = AttributeKey.stringKey("channel");
    private static final AttributeKey<String> CALLER = AttributeKey.stringKey("caller");
    private static final AttributeKey<String> DECISION = AttributeKey.stringKey("decision");

    /**
     * What routing decided about a channel, before any transport was invoked.
     * <p>
     * Only {@code ATTEMPTED} is followed by a {@code msg.transmit} recording, which is the whole
     * point of keeping the two apart: every message passes through every channel here, so
     * {@code msg.route} is by construction the same curve for all three, while {@code msg.transmit}
     * is not. {@code GATED} is the normal case for SMS rather than a fault — that gate requires
     * both an inactive team and a critical message type.
     */
    public enum RouteDecision {
        ATTEMPTED,
        GATED,
        NOT_SUPPORTED
    }

    /** How a channel attempt ended, once a transport was actually invoked. */
    public enum TransmitOutcome {
        SUCCESS,
        FAILURE,
        SKIPPED
    }

    /**
     * Outcome of one FCM HTTP call. The non-transport constants mirror the {@code errorCode} in
     * the FCM error payload, which is the only way to tell a dead token apart from a quota
     * problem or an outage.
     */
    public enum FcmOutcome {
        OK,
        UNREGISTERED,
        INVALID_ARGUMENT,
        RATE_LIMITED,
        UNAVAILABLE,
        AUTH_ERROR,
        DISABLED,
        TRANSPORT_ERROR,
        OTHER
    }

    /** Which code path drove an FCM send; the two do not share a transmitter. */
    public enum FcmCaller {
        TEAM_MESSAGE,
        CHECKPOINT_OPERATOR
    }

    /**
     * Outcome of one Twilio send. The status constants are Twilio's own message statuses as
     * reported at creation time — a later terminal failure is never observed, because there is
     * no status-callback webhook.
     */
    public enum SmsOutcome {
        QUEUED,
        ACCEPTED,
        SENT,
        DELIVERED,
        FAILED,
        UNDELIVERED,
        API_ERROR,
        DISABLED,
        NOT_INITIALIZED,
        NO_NUMBER,
        OTHER
    }

    @Inject
    Meters meters;

    private LongCounter route;
    private LongCounter transmit;
    private LongCounter fcmRequest;
    private LongCounter fcmFanout;
    private LongCounter smsSend;

    @PostConstruct
    void init() {
        Meter meter = meters.meter(SCOPE);
        route = meter.counterBuilder("msg.route")
                .setUnit("{decision}")
                .setDescription("Per-channel routing decisions taken before any transport was invoked")
                .build();
        transmit = meter.counterBuilder("msg.transmit")
                .setUnit("{transmission}")
                .setDescription("Message delivery attempts that reached a transport, by channel and outcome")
                .build();
        fcmRequest = meter.counterBuilder("fcm.request")
                .setUnit("{request}")
                .setDescription("Firebase Cloud Messaging HTTP calls, by outcome")
                .build();
        fcmFanout = meter.counterBuilder("fcm.fanout")
                .setUnit("{token}")
                .setDescription("Device tokens a push was fanned out to, by calling path")
                .build();
        smsSend = meter.counterBuilder("sms.send")
                .setUnit("{message}")
                .setDescription("Twilio SMS sends, by outcome")
                .build();
    }

    public void routed(TransmissionChannel channel, RouteDecision decision) {
        route.add(1, Attributes.of(CHANNEL, Meters.tag(channel), DECISION, Meters.tag(decision)));
    }

    public void transmitted(TransmissionChannel channel, TransmitOutcome outcome) {
        transmit.add(1, Attributes.of(CHANNEL, Meters.tag(channel), Meters.OUTCOME, Meters.tag(outcome)));
    }

    public void fcmRequest(FcmOutcome outcome) {
        fcmRequest.add(1, Attributes.of(Meters.OUTCOME, Meters.tag(outcome)));
    }

    public void fcmFanout(FcmCaller caller, long tokens) {
        if (tokens > 0) {
            fcmFanout.add(tokens, Attributes.of(CALLER, Meters.tag(caller)));
        }
    }

    public void smsSent(SmsOutcome outcome) {
        smsSend.add(1, Attributes.of(Meters.OUTCOME, Meters.tag(outcome)));
    }
}
