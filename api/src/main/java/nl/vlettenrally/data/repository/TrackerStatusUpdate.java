package nl.vlettenrally.data.repository;

import java.time.Instant;

/**
 * One tracker's health snapshot from a polled location feed, applied via
 * {@link TrackerRepository#updateStatuses}.
 */
public record TrackerStatusUpdate(
        String trackerId,
        Instant lastSeenAt,
        Integer batteryPercentage,
        Double latitude,
        Double longitude
) {}
