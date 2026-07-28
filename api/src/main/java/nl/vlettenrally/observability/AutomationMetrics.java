package nl.vlettenrally.observability;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.enums.AutomationExecutionStatus;
import nl.vlettenrally.data.enums.AutomationTriggerType;

/**
 * Metrics for the automation engine.
 * <p>
 * Rule firings are low-frequency (a handful per minute at most), so attributes are built per
 * recording rather than pre-allocated.
 */
@Startup
@ApplicationScoped
public class AutomationMetrics {

    private static final String SCOPE = "nl.vlettenrally.automation";

    private static final AttributeKey<String> TRIGGER = AttributeKey.stringKey("trigger");
    private static final AttributeKey<String> ACTION = AttributeKey.stringKey("action");
    private static final AttributeKey<String> HOOK = AttributeKey.stringKey("hook");

    /** Why a rule that matched its trigger was not executed. Invisible before this metric. */
    public enum SkipReason {
        DUPLICATE,
        COOLDOWN,
        TEAM_MISSING,
        TEAM_FINISHED,
        NO_TARGETS
    }

    /** The engine entry points, each of which swallows its exceptions to protect the caller. */
    public enum Hook {
        CHECKPOINT,
        GAME_STATUS,
        OVERTAKE,
        SPEED,
        CHECK_IN,
        PICTURE,
        DISTANCE,
        DISTANCE_RESET,
        SCHEDULED_RULE,
        INACTIVITY_RULE
    }

    @Inject
    Meters meters;

    private LongCounter fire;
    private LongCounter skip;
    private LongCounter actionError;
    private LongCounter hookError;

    @PostConstruct
    void init() {
        Meter meter = meters.meter(SCOPE);
        fire = meter.counterBuilder("automation.fire")
                .setUnit("{firing}")
                .setDescription("Automation rules executed, by trigger type and execution status")
                .build();
        skip = meter.counterBuilder("automation.skip")
                .setUnit("{firing}")
                .setDescription("Automation rules that matched their trigger but were not executed")
                .build();
        actionError = meter.counterBuilder("automation.action.error")
                .setUnit("{action}")
                .setDescription("Individual automation actions that threw during execution")
                .build();
        hookError = meter.counterBuilder("automation.hook.error")
                .setUnit("{error}")
                .setDescription("Exceptions swallowed by an automation engine hook")
                .build();
    }

    public void fired(AutomationTriggerType trigger, AutomationExecutionStatus status) {
        fire.add(1, Attributes.of(TRIGGER, Meters.tag(trigger), Meters.STATUS, Meters.tag(status)));
    }

    public void skipped(SkipReason reason) {
        skip.add(1, Attributes.of(Meters.REASON, Meters.tag(reason)));
    }

    /** {@code action} is the simple class name of the {@code AutomationAction} subtype. */
    public void actionFailed(String action) {
        actionError.add(1, Attributes.of(ACTION, action));
    }

    public void hookFailed(Hook hook) {
        hookError.add(1, Attributes.of(HOOK, Meters.tag(hook)));
    }
}
