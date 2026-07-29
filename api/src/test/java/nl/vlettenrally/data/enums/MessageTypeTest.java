package nl.vlettenrally.data.enums;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Guards the delivery-policy predicates on {@link MessageType}.
 * <p>
 * They decide what happens to a message the participant did not see immediately, and they
 * fail silently when wrong: a type wrongly marked ephemeral is dropped without a trace, one
 * wrongly marked non-urgent arrives minutes late, and one wrongly marked critical wakes teams
 * by SMS. These tests exist to make a change to any of them deliberate.
 */
class MessageTypeTest {

    @Test
    void onlyGeneralIsDeliveredAtNormalUrgency() {
        Set<MessageType> notUrgent = EnumSet.noneOf(MessageType.class);
        for (MessageType type : MessageType.values()) {
            if (!type.isUrgent()) {
                notUrgent.add(type);
            }
        }

        assertEquals(
            EnumSet.of(MessageType.GENERAL),
            notUrgent,
            "GENERAL makes no promise about timeliness; every other type is generated at a moment that matters");
    }

    @Test
    void urgencyAndExpiryAreIndependent() {
        // GENERAL is neither: delivered whenever convenient, but never dropped.
        assertFalse(MessageType.GENERAL.isUrgent());
        assertFalse(MessageType.GENERAL.isEphemeral());

        // CHECKPOINT is urgent but permanent: wake the device, and never discard it.
        assertTrue(MessageType.CHECKPOINT.isUrgent());
        assertFalse(MessageType.CHECKPOINT.isEphemeral());

        // COUNTDOWN is both: now or never.
        assertTrue(MessageType.COUNTDOWN.isUrgent());
        assertTrue(MessageType.COUNTDOWN.isEphemeral());
    }

    @Test
    void ephemeralTypesAreExactlyTheUrgentAndDisposableOnes() {
        Set<MessageType> ephemeral = EnumSet.noneOf(MessageType.class);
        for (MessageType type : MessageType.values()) {
            if (type.isEphemeral()) {
                ephemeral.add(type);
            }
        }

        assertEquals(
            EnumSet.of(MessageType.COUNTDOWN, MessageType.SPEED_WARNING, MessageType.RIVAL),
            ephemeral,
            "Only types that are worthless when stale may expire - everything else must be delivered whenever it lands");
    }

    @Test
    void achievementAndCheckpointSurviveDelay() {
        // These are a record of points and arrivals: late is fine, missing is not.
        assertFalse(MessageType.ACHIEVEMENT.isEphemeral());
        assertFalse(MessageType.CHECKPOINT.isEphemeral());
    }

    @Test
    void rallydeskIsNeverDropped() {
        // The one type important enough to escalate to SMS must never be allowed to expire.
        assertTrue(MessageType.RALLYDESK.isCritical());
        assertFalse(MessageType.RALLYDESK.isEphemeral());
    }

    @Test
    void generalIsTreatedAsUnknownContent() {
        // The catch-all carries arbitrary text, so it gets the conservative treatment.
        assertFalse(MessageType.GENERAL.isEphemeral());
    }

    @Test
    void criticalIsASeparateAxisFromEphemeral() {
        // Reusing isCritical() for TTL would expire everything except RALLYDESK.
        assertNotEquals(
            MessageType.ACHIEVEMENT.isCritical(),
            MessageType.RALLYDESK.isCritical(),
            "isCritical must stay meaningful independently of isEphemeral");
        assertFalse(MessageType.ACHIEVEMENT.isCritical());
        assertFalse(MessageType.ACHIEVEMENT.isEphemeral());
    }
}
