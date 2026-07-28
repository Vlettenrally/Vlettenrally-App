package nl.vlettenrally.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;

/**
 * Drives the time-based automation triggers on two separate cadences:
 * <ul>
 *   <li>SCHEDULED_TIME — latency-sensitive, evaluated on the fast
 *       {@code vlettenrally.automation.tick-interval} (default 5s).</li>
 *   <li>TEAM_INACTIVITY — threshold is in minutes and the per-team location
 *       lookup is the heaviest part of the sweep, so it runs on the coarse
 *       {@code vlettenrally.automation.inactivity-interval} (default 30s).</li>
 * </ul>
 *
 * Both ticks run on a single instance at a time — leader election via
 * {@link PollingLeader} (Valkey-backed lease, shared with the other
 * background schedulers).
 */
@ApplicationScoped
public class AutomationScheduler {

    @Inject
    AutomationEngine automationEngine;

    @Inject
    PollingLeader pollingLeader;

    @Scheduled(every = "{vlettenrally.automation.tick-interval}",
               concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    @Transactional
    void tickScheduledTime() {
        if (!pollingLeader.tryAcquireOrRenew()) {
            return;
        }
        automationEngine.evaluateScheduledTime(Instant.now());
    }

    @Scheduled(every = "{vlettenrally.automation.inactivity-interval}",
               concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    @Transactional
    void tickInactivity() {
        if (!pollingLeader.tryAcquireOrRenew()) {
            return;
        }
        automationEngine.evaluateInactivity(Instant.now());
    }
}
