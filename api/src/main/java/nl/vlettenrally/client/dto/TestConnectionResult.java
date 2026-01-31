package nl.vlettenrally.client.dto;

/**
 * Result of testing a location endpoint connection.
 */
public class TestConnectionResult {
    private final boolean success;
    private final int participantCount;
    private final String errorMessage;

    public TestConnectionResult(boolean success, int participantCount, String errorMessage) {
        this.success = success;
        this.participantCount = participantCount;
        this.errorMessage = errorMessage;
    }

    public boolean isSuccess() {
        return success;
    }

    public int getParticipantCount() {
        return participantCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
