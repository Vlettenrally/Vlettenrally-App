package nl.vlettenrally.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nl.vlettenrally.data.repository.TrackerRepository;
import nl.vlettenrally.data.repository.TrackerStatusUpdate;

/**
 * Unit tests for TrackerStatusRecorder: status persistence is best-effort and
 * must never propagate a failure into the polling loop.
 */
@ExtendWith(MockitoExtension.class)
class TrackerStatusRecorderTest {

    @Mock
    TrackerRepository trackerRepository;

    @InjectMocks
    TrackerStatusRecorder recorder;

    @Test
    void record_delegatesToRepository() {
        List<TrackerStatusUpdate> updates = List.of(
                new TrackerStatusUpdate("T1", Instant.now(), 80, 52.0, 5.1));

        recorder.record(updates);

        verify(trackerRepository).updateStatuses(updates);
    }

    @Test
    void record_emptyOrNull_isNoOp() {
        recorder.record(List.of());
        recorder.record(null);

        verify(trackerRepository, never()).updateStatuses(anyCollection());
    }

    @Test
    void record_swallowsRepositoryFailure() {
        doThrow(new RuntimeException("db down")).when(trackerRepository).updateStatuses(anyCollection());

        assertDoesNotThrow(() -> recorder.record(List.of(
                new TrackerStatusUpdate("T1", Instant.now(), 80, 52.0, 5.1))));
    }
}
