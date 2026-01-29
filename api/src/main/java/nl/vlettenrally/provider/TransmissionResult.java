package nl.vlettenrally.provider;

import nl.vlettenrally.data.enums.TransmissionChannel;

/**
 * Result of a message transmission attempt.
 */
public class TransmissionResult {
    private final boolean success;
    private final TransmissionChannel channel;
    private final String errorMessage;

    private TransmissionResult(boolean success, TransmissionChannel channel, String errorMessage) {
        this.success = success;
        this.channel = channel;
        this.errorMessage = errorMessage;
    }

    /**
     * Create a successful transmission result.
     */
    public static TransmissionResult success(TransmissionChannel channel) {
        return new TransmissionResult(true, channel, null);
    }

    /**
     * Create a failed transmission result.
     */
    public static TransmissionResult failure(TransmissionChannel channel, String errorMessage) {
        return new TransmissionResult(false, channel, errorMessage);
    }

    /**
     * Create a skipped transmission result (e.g., no token available).
     */
    public static TransmissionResult skipped(TransmissionChannel channel, String reason) {
        return new TransmissionResult(false, channel, "Skipped: " + reason);
    }

    public boolean isSuccess() { return success; }
    public TransmissionChannel getChannel() { return channel; }
    public String getErrorMessage() { return errorMessage; }

    @Override
    public String toString() {
        if (success) {
            return "TransmissionResult{success=true, channel=" + channel + "}";
        } else {
            return "TransmissionResult{success=false, channel=" + channel + ", error='" + errorMessage + "'}";
        }
    }
}
