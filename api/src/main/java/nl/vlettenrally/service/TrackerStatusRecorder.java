package nl.vlettenrally.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.vlettenrally.data.repository.TrackerRepository;
import nl.vlettenrally.data.repository.TrackerStatusUpdate;

import java.util.Collection;

/**
 * Persists tracker health snapshots (last seen, battery, last position) captured
 * from a polled location feed. Failures are logged and swallowed: status is a
 * nice-to-have and must never break location ingestion.
 */
@ApplicationScoped
public class TrackerStatusRecorder {

    @Inject
    TrackerRepository trackerRepository;

    @Transactional
    public void record(Collection<TrackerStatusUpdate> updates) {
        if (updates == null || updates.isEmpty()) {
            return;
        }
        try {
            trackerRepository.updateStatuses(updates);
        } catch (Exception e) {
            Log.warnf("Failed to record tracker statuses (%d updates): %s", updates.size(), e.getMessage());
        }
    }
}
