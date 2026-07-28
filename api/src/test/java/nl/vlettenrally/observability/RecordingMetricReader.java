package nl.vlettenrally.observability;

import java.util.Collection;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.CollectionRegistration;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import org.jspecify.annotations.NonNull;

/**
 * Minimal in-memory {@link MetricReader} for tests: collects on demand instead of on a timer.
 * <p>
 * Hand-rolled rather than pulling in {@code opentelemetry-sdk-testing} — the interface is four
 * methods, and the alternative is a test-only dependency for a single class.
 */
final class RecordingMetricReader implements MetricReader {

    private volatile CollectionRegistration registration = CollectionRegistration.noop();

    @Override
    public void register(@NonNull CollectionRegistration registration) {
        this.registration = registration;
    }

    /** Collect everything recorded since the provider was built. */
    Collection<MetricData> collect() {
        return registration.collectAllMetrics();
    }

    @Override
    public AggregationTemporality getAggregationTemporality(@NonNull InstrumentType instrumentType) {
        return AggregationTemporality.CUMULATIVE;
    }

    @Override
    public CompletableResultCode forceFlush() {
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
        return CompletableResultCode.ofSuccess();
    }
}
