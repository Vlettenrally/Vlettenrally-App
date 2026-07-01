package nl.vlettenrally.websockets;

/**
 * Admin sub-page "topics" used to gate admin-only WebSocket broadcasts behind
 * per-page heartbeats (see {@link AdminTopicSubscriptionManager}). Each value
 * must match the topic string sent by the corresponding admin page's
 * ADMIN_HEARTBEAT (frontend {@code ADMIN_TOPICS}).
 */
public final class AdminTopics {

    private AdminTopics() {
        // Prevent instantiation
    }

    /** Pictures moderation queue — PICTURE_PENDING / PICTURE_MODERATED. */
    public static final String PICTURES = "pictures";

    /** Points Management — POINT_ENTRY_CREATED / POINT_ENTRY_UPDATED. */
    public static final String POINTS = "points";

    /** Messages — MESSAGE_DELIVERY_UPDATE. */
    public static final String MESSAGES = "messages";

    /** Automations — AUTOMATION_TRIGGERED. */
    public static final String AUTOMATIONS = "automations";

    /** Admin inbox — AUTOMATION_NOTIFICATION. */
    public static final String INBOX = "inbox";
}
