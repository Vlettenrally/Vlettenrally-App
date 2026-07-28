package nl.vlettenrally.observability;

import java.util.Locale;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.metrics.Meter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Shared entry point for building application metrics.
 * <p>
 * Always obtain a {@link Meter} through here rather than via
 * {@code GlobalOpenTelemetry.getMeter(...)}. {@code GlobalOpenTelemetry.get()} permanently caches
 * a no-op instance if it is called before the SDK registers itself, and the SDK only registers
 * when Quarkus creates the {@link OpenTelemetry} bean. Injecting that bean is what triggers the
 * registration, so there is no ordering race to work around.
 * <p>
 * When {@code quarkus.otel.metrics.enabled} is false at build time (dev, test and mock) the meter
 * provider is a no-op: instruments still build, recordings go nowhere, nothing throws.
 */
@ApplicationScoped
public class Meters {

    /** Why something was dropped, skipped or retried. */
    public static final AttributeKey<String> REASON = AttributeKey.stringKey("reason");
    /** How an attempt ended. The default key for anything with more than two endings. */
    public static final AttributeKey<String> OUTCOME = AttributeKey.stringKey("outcome");
    /** A domain status that already has its own enum (e.g. an automation execution status). */
    public static final AttributeKey<String> STATUS = AttributeKey.stringKey("status");

    @Inject
    OpenTelemetry openTelemetry;

    public Meter meter(String instrumentationScope) {
        return openTelemetry.getMeter(instrumentationScope);
    }

    /**
     * Render an enum constant as a metric attribute value: {@code CHECKPOINT_ENTRY} becomes
     * {@code checkpoint_entry}. Keeping the mapping mechanical means a new enum constant can
     * never silently become an unlabelled series.
     */
    public static String tag(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
