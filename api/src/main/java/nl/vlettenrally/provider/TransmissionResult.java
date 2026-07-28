package nl.vlettenrally.provider;

import lombok.Getter;
import nl.vlettenrally.data.enums.TransmissionChannel;

/**
 * Result of a message transmission attempt.
 */
@Getter
public class TransmissionResult {
    private final boolean success;
    private final boolean skipped;
    private final TransmissionChannel channel;
    private final String errorMessage;

    private TransmissionResult(boolean success, boolean skipped, TransmissionChannel channel, String errorMessage) {
        this.success = success;
        this.skipped = skipped;
        this.channel = channel;
        this.errorMessage = errorMessage;
    }

    /**
     * Create a successful transmission result.
     */
    public static TransmissionResult success(TransmissionChannel channel) {
        return new TransmissionResult(true, false, channel, null);
    }

    /**
     * Create a failed transmission result.
     */
    public static TransmissionResult failure(TransmissionChannel channel, String errorMessage) {
        return new TransmissionResult(false, false, channel, errorMessage);
    }

    /**
     * Create a skipped transmission result (e.g., no token available).
     * <p>
     * A skip is not a failure: the channel was never attempted because it had nothing to work
     * with. The distinction is carried by {@link #isSkipped()} rather than by the {@code "Skipped: "}
     * prefix on the message, so callers never have to inspect the error string to tell them apart.
     */
    public static TransmissionResult skipped(TransmissionChannel channel, String reason) {
        return new TransmissionResult(false, true, channel, "Skipped: " + reason);
    }

    @Override
    public String toString() {
        if (success) {
            return "TransmissionResult{success=true, channel=" + channel + "}";
        } else {
            return "TransmissionResult{success=false, channel=" + channel + ", error='" + errorMessage + "'}";
        }
    }
}
