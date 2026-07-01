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

    /** Live map — LOCATION_UPDATE. Scope: game id. */
    public static final String MAP = "map";

    /** Pictures moderation queue — PICTURE_PENDING / PICTURE_MODERATED. Scope: game id. */
    public static final String PICTURES = "pictures";

    /** Points Management — POINT_ENTRY_CREATED / POINT_ENTRY_UPDATED. Scope: game id. */
    public static final String POINTS = "points";

    /** Messages — MESSAGE_DELIVERY_UPDATE. Scope: game id. */
    public static final String MESSAGES = "messages";

    /** Automations — AUTOMATION_TRIGGERED. Scope: game id. */
    public static final String AUTOMATIONS = "automations";

    /** Admin inbox — AUTOMATION_NOTIFICATION. Scope: game id. */
    public static final String INBOX = "inbox";

    /** Checkpoint station — CHECKPOINT_DETECTION. Scope: checkpoint id (not game id). */
    public static final String CHECKPOINT = "checkpoint";

    /**
     * Game-scoped topics (scope = game id). Used to drop every subscription for a
     * game at once on game-switch / disconnect. Excludes {@link #CHECKPOINT}, which
     * is scoped by checkpoint id and torn down separately.
     */
    public static final String[] GAME_SCOPED = { MAP, PICTURES, POINTS, MESSAGES, AUTOMATIONS, INBOX };
}
