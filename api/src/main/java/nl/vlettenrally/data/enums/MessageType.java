package nl.vlettenrally.data.enums;

/**
 * Type of a Message.
 */
public enum MessageType {
    GENERAL,
    COUNTDOWN,
    CHECKPOINT,
    RALLYDESK,
    ACHIEVEMENT,
    RIVAL,
    SPEED_WARNING;

    /**
     * Check if this message type is critical and should trigger SMS fallback
     * when the team's device is inactive.
     *
     * @return true if this is a critical message type
     */
    public boolean isCritical() {
        return this == RALLYDESK;
    }

    /**
     * Check if this message type is worth waking a sleeping device for.
     * <p>
     * Everything the rally generates on purpose is timed — a countdown, a checkpoint arrival,
     * a rallydesk announcement — and is worth interrupting for. {@code GENERAL} is the
     * catch-all whose content is arbitrary and which makes no promise about timeliness, so it
     * rides along with whatever wakeup the device was going to do anyway.
     *
     * @return true if this message type should be pushed at high urgency
     */
    public boolean isUrgent() {
        return this != GENERAL;
    }

    /**
     * Check if this message type loses its value once it is stale, so a push that cannot be
     * delivered promptly should expire rather than surface late.
     * <p>
     * This is a different axis from {@link #isCritical()}, which asks whether a message is
     * worth escalating to SMS. Ephemeral means both urgent <em>and</em> cheap to lose: a
     * countdown that arrives after the moment has passed is noise, a stale speed warning is
     * actively misleading, and a rival update is superseded by the next one. Types left out
     * are either not urgent ({@code ACHIEVEMENT}, {@code CHECKPOINT} — a record of points and
     * arrivals worth having whenever it lands) or too important to drop ({@code RALLYDESK},
     * and {@code GENERAL} whose content is unknown).
     *
     * @return true if a late delivery of this message type is worse than no delivery
     */
    public boolean isEphemeral() {
        return this == COUNTDOWN || this == SPEED_WARNING || this == RIVAL;
    }
}
