package nl.vlettenrally.data.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the checkpoint opening-window helper that gates automatic
 * scoring and automations.
 */
class CheckpointTest {

    private static final Instant OPEN = Instant.parse("2026-03-09T10:00:00Z");
    private static final Instant CLOSE = Instant.parse("2026-03-09T14:00:00Z");

    private Checkpoint checkpoint(Instant opening, Instant closing) {
        CircleCheckpoint cp = new CircleCheckpoint();
        cp.setOpeningTime(opening);
        cp.setClosingTime(closing);
        return cp;
    }

    @Test
    void alwaysOpen_whenNoBounds() {
        Checkpoint cp = checkpoint(null, null);
        assertTrue(cp.isOpenAt(OPEN));
        assertTrue(cp.isOpenAt(Instant.EPOCH));
    }

    @Test
    void openWithinWindow_andClosedOutside() {
        Checkpoint cp = checkpoint(OPEN, CLOSE);
        assertTrue(cp.isOpenAt(Instant.parse("2026-03-09T12:00:00Z")));
        assertFalse(cp.isOpenAt(Instant.parse("2026-03-09T09:59:59Z")));
        assertFalse(cp.isOpenAt(Instant.parse("2026-03-09T14:00:01Z")));
    }

    @Test
    void boundaryInstantsCountAsOpen() {
        Checkpoint cp = checkpoint(OPEN, CLOSE);
        assertTrue(cp.isOpenAt(OPEN));
        assertTrue(cp.isOpenAt(CLOSE));
    }

    @Test
    void openingOnly_isOpenFromThatTime() {
        Checkpoint cp = checkpoint(OPEN, null);
        assertFalse(cp.isOpenAt(OPEN.minusSeconds(1)));
        assertTrue(cp.isOpenAt(OPEN.plusSeconds(1)));
    }

    @Test
    void closingOnly_isOpenUntilThatTime() {
        Checkpoint cp = checkpoint(null, CLOSE);
        assertTrue(cp.isOpenAt(CLOSE.minusSeconds(1)));
        assertFalse(cp.isOpenAt(CLOSE.plusSeconds(1)));
    }

    @Test
    void nullInstant_isTreatedAsOpen() {
        assertTrue(checkpoint(OPEN, CLOSE).isOpenAt(null));
    }
}
