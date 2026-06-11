package nl.vlettenrally.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;

/**
 * Drives the time-based automation triggers (SCHEDULED_TIME, TEAM_INACTIVITY).
 *
 * Runs on a single instance at a time — leader election via
 * {@link PollingLeader} (Valkey-backed lease, shared with the other
 * background schedulers).
 */
@ApplicationScoped
public class AutomationScheduler {

    @Inject
    AutomationEngine automationEngine;

    @Inject
    PollingLeader pollingLeader;

    @Scheduled(every = "30s")
    @Transactional
    void tick() {
        if (!pollingLeader.tryAcquireOrRenew()) {
            return;
        }
        automationEngine.evaluateTimeAndInactivity(Instant.now());
    }
}
