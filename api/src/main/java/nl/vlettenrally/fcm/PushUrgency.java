package nl.vlettenrally.fcm;

/**
 * The Web Push {@code Urgency} header, which tells the push service how much it may trade
 * timeliness for the recipient's battery.
 * <p>
 * Every token in this project is a browser token, so FCM delivers over the Web Push protocol
 * and this header decides whether Android hands the message over immediately or holds it
 * until the next Doze maintenance window — minutes, lengthening the longer the phone stays
 * idle. Only the two values this project uses are defined; the spec also allows
 * {@code very-low} and {@code low}.
 */
public enum PushUrgency {

    /**
     * Deliver when convenient. The device may batch this with other wakeups to save battery,
     * so it can land minutes late. Right for content with no promise of timeliness.
     */
    NORMAL("normal"),

    /**
     * Wake the device now. Right for anything whose meaning depends on arriving promptly —
     * a pocketed phone is the normal state during a rally, so this is not the exception.
     */
    HIGH("high");

    private final String headerValue;

    PushUrgency(String headerValue) {
        this.headerValue = headerValue;
    }

    /** The literal value to send in the {@code Urgency} header. */
    public String headerValue() {
        return headerValue;
    }
}
