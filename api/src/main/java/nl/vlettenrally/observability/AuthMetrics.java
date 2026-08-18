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
 * Metrics for refresh-token rotation and replay handling.
 * <p>
 * Before these existed, the only signal that the replay tripwire had fired was a single WARN line,
 * so there was no way to tell an attack from a client that simply lost a rotation response. The
 * enums below are the shared vocabulary for both the log lines and the metric attributes — keeping
 * them in one place means a new outcome cannot become an unlabelled series.
 * <p>
 * Subject / team / device ids deliberately never become attributes (cardinality); they belong in
 * the log lines, which carry the same vocabulary so the two can be correlated.
 */
@Startup
@ApplicationScoped
public class AuthMetrics {

    private static final String SCOPE = "nl.vlettenrally.auth";

    private static final AttributeKey<String> PRINCIPAL = AttributeKey.stringKey("principal");
    private static final AttributeKey<String> RESOLUTION = AttributeKey.stringKey("resolution");
    private static final AttributeKey<String> LOOKUP = AttributeKey.stringKey("lookup");
    private static final AttributeKey<String> HOPS = AttributeKey.stringKey("hops");
    private static final AttributeKey<Boolean> STALE = AttributeKey.booleanKey("stale");
    private static final AttributeKey<String> REVOKE_SCOPE = AttributeKey.stringKey("scope");

    /** How a refresh request ended. */
    public enum RefreshResult {
        /** Normal rotation: the caller won the race and got a brand new pair. */
        ROTATED,
        /** Replay resolved to the chain's live head; the caller converges instead of being locked out. */
        REPLAY_HEAD,
        /** Unresolvable right now (concurrent duplicate, missing link) — the client should retry. */
        RETRYABLE,
        /** Definitively refused: unknown, expired, or the subject no longer exists. */
        REJECTED,
        /** Refused AND the token's family was revoked (the replay tripwire). */
        REVOKED
    }

    public enum Principal { TEAM, ADMIN }

    /** Where a presented-but-revoked token's chain ended up. */
    public enum ChainOutcome {
        /** A still-usable successor was found. */
        LIVE_HEAD,
        /** The chain ends here: revoked, but nothing recorded as its successor. */
        DEAD_END,
        /** A successor is recorded but its row is gone (cleanup, logout, check-in reset). */
        MISSING_LINK,
        /** The chain resolves, but its head has expired. */
        EXPIRED_END,
        /** Walk gave up: hop limit reached, or a cycle. */
        HOP_LIMIT
    }

    /** How the head was looked up — the family index, or the legacy replaced_by_token walk. */
    public enum ChainLookup { FAMILY, CHAIN_WALK }

    /** How wide a revocation reached. {@code FAMILY} is one device's chain; {@code SUBJECT} is everything. */
    public enum RevokeScope { FAMILY, SUBJECT }

    @Inject
    Meters meters;

    private LongCounter refresh;
    private LongCounter replay;
    private LongCounter revoke;

    @PostConstruct
    void init() {
        Meter meter = meters.meter(SCOPE);
        refresh = meter.counterBuilder("auth.refresh")
                .setUnit("{request}")
                .setDescription("Refresh-token requests, by how they ended and who asked")
                .build();
        replay = meter.counterBuilder("auth.refresh.replay")
                .setUnit("{replay}")
                .setDescription("Refreshes presenting an already-rotated token, by how far behind the caller was")
                .build();
        revoke = meter.counterBuilder("auth.refresh.revoke")
                .setUnit("{revocation}")
                .setDescription("Token revocations triggered by the replay tripwire, by scope")
                .build();
    }

    public void refreshed(RefreshResult result, Principal principal) {
        refresh.add(1, Attributes.of(Meters.OUTCOME, Meters.tag(result), PRINCIPAL, Meters.tag(principal)));
    }

    /**
     * @param hops how many successor links were walked; negative when the family index answered
     *             directly and the distance is therefore unknown
     * @param stale whether the replay arrived after the configured replay window — served anyway,
     *              but worth watching
     */
    public void replayed(ChainOutcome outcome, ChainLookup lookup, int hops, boolean stale) {
        replay.add(1, Attributes.of(
                RESOLUTION, Meters.tag(outcome),
                LOOKUP, Meters.tag(lookup),
                HOPS, hopBucket(hops),
                STALE, stale));
    }

    public void revoked(RevokeScope scope, Principal principal) {
        revoke.add(1, Attributes.of(REVOKE_SCOPE, Meters.tag(scope), PRINCIPAL, Meters.tag(principal)));
    }

    private static String hopBucket(int hops) {
        if (hops < 0) {
            return "unknown";
        }
        return switch (hops) {
            case 1 -> "1";
            case 2 -> "2";
            default -> "3plus";
        };
    }
}
