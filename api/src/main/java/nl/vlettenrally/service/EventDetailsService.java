package nl.vlettenrally.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.client.LocationApiClient;
import nl.vlettenrally.client.dto.legendstracking.EventCheckpoint;
import nl.vlettenrally.client.dto.legendstracking.EventDetailsBody;
import nl.vlettenrally.client.dto.legendstracking.EventDetailsResponse;
import nl.vlettenrally.client.exception.LocationApiException;
import nl.vlettenrally.data.domain.Checkpoint;
import nl.vlettenrally.data.domain.CircleCheckpoint;
import nl.vlettenrally.data.domain.PolygonCheckpoint;
import nl.vlettenrally.observability.LocationMetrics;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for syncing master data from the Legendstracking /details endpoint.
 * Upserts checkpoints (zones) by externalId ↔ EventCheckpoint.id.
 */
@ApplicationScoped
public class EventDetailsService {

    private static final double CHECKPOINT_MATCH_TOLERANCE_METERS = 50.0;
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Inject
    LocationApiClient locationApiClient;

    @Inject
    CheckpointService checkpointService;

    /**
     * Import checkpoints (zones) for a game from the tracker /details endpoint.
     *
     * @param gameId              the game to import checkpoints into
     * @param locationEndpointUrl the tracker /details endpoint URL
     * @return counts of created, updated, and errored checkpoints
     */
    public ImportResult importCheckpoints(UUID gameId, String locationEndpointUrl)
            throws LocationApiException {
        EventDetailsBody body = fetchDetailsBody(locationEndpointUrl);
        List<Checkpoint> existingCheckpoints = checkpointService.findByGameId(gameId);
        Map<String, Checkpoint> checkpointsByExternalId = indexByExternalId(existingCheckpoints);

        int created = 0;
        int updated = 0;
        int errors = 0;

        List<EventCheckpoint> checkpoints = body.getEventCheckpoints();
        if (checkpoints != null) {
            for (EventCheckpoint detail : checkpoints) {
                try {
                    String externalId = String.valueOf(detail.getId());
                    Checkpoint existing = checkpointsByExternalId.get(externalId);
                    if (existing == null) {
                        existing = matchCheckpointByLocation(detail, existingCheckpoints).orElse(null);
                    }

                    if (existing != null) {
                        boolean changed = false;

                        if (!Objects.equals(existing.getExternalId(), externalId)) {
                            existing.setExternalId(externalId);
                            changed = true;
                        }

                        if (detail.getLocation() != null) {
                            Double newLat = detail.getLocation().getLatitude();
                            Double newLng = detail.getLocation().getLongitude();
                            if (!Objects.equals(existing.getLatitude(), newLat)
                                    || !Objects.equals(existing.getLongitude(), newLng)) {
                                existing.setLatitude(newLat);
                                existing.setLongitude(newLng);
                                changed = true;
                            }
                        }

                        if (detail.getName() != null && !detail.getName().equals(existing.getName())) {
                            existing.setName(detail.getName());
                            changed = true;
                        }

                        if (changed) {
                            existing.setUpdatedAt(Instant.now());
                            checkpointService.save(existing);
                            checkpointsByExternalId.put(externalId, existing);
                            updated++;
                        }
                    } else {
                        // No existing app zone matched — create a new checkpoint and take its
                        // geofence geometry from the API (polygon when provided, else a circle
                        // with the API radius). Matched zones above keep their app geometry.
                        Checkpoint cp = buildImportedCheckpoint(gameId, externalId, detail);
                        checkpointService.save(cp);
                        existingCheckpoints.add(cp);
                        checkpointsByExternalId.put(externalId, cp);
                        created++;
                    }
                } catch (Exception e) {
                    errors++;
                }
            }
        }

        return new ImportResult(created, updated, errors);
    }

    /** Fetches the /details event body, throwing if the response is malformed. */
    private EventDetailsBody fetchDetailsBody(String locationEndpointUrl) throws LocationApiException {
        EventDetailsResponse details = locationApiClient.fetchDetails(
            locationEndpointUrl, LocationMetrics.ApiCaller.EVENT_IMPORT);
        EventDetailsBody body = details != null ? details.getEvent() : null;
        if (body == null) {
            throw new LocationApiException("Malformed /details response: missing 'event'");
        }
        return body;
    }

    private static Map<String, Checkpoint> indexByExternalId(List<Checkpoint> checkpoints) {
        Map<String, Checkpoint> byExternalId = new HashMap<>();
        for (Checkpoint cp : checkpoints) {
            if (cp.getExternalId() != null && !cp.getExternalId().isBlank()) {
                byExternalId.put(cp.getExternalId(), cp);
            }
        }
        return byExternalId;
    }

    /**
     * Build a brand-new checkpoint from an API detail, deriving its geofence shape from
     * the API: a {@link PolygonCheckpoint} when {@code polygon_coordinates} describes at
     * least a triangle, otherwise a {@link CircleCheckpoint} using the API radius (falling
     * back to the 50 m default when absent or non-positive).
     */
    private static Checkpoint buildImportedCheckpoint(UUID gameId, String externalId, EventCheckpoint detail) {
        Polygon polygon = parsePolygon(detail.getPolygonCoordinates());

        Checkpoint cp;
        if (polygon != null) {
            PolygonCheckpoint poly = new PolygonCheckpoint();
            poly.setZonePolygon(polygon);
            cp = poly;
        } else {
            CircleCheckpoint circle = new CircleCheckpoint();
            if (detail.getRadius() != null && detail.getRadius() > 0) {
                circle.setRadiusMeters(detail.getRadius());
            }
            cp = circle;
        }

        cp.setGameId(gameId);
        cp.setExternalId(externalId);
        cp.setName(detail.getName() != null ? detail.getName() : "Zone " + externalId);
        cp.setPoints(0);
        if (detail.getLocation() != null) {
            cp.setLatitude(detail.getLocation().getLatitude());
            cp.setLongitude(detail.getLocation().getLongitude());
        }
        return cp;
    }

    /**
     * Parse a Legendstracking {@code polygon_coordinates} string — a flat comma-separated
     * list of {@code lng,lat} pairs, e.g.
     * {@code "4.668807,52.025402,4.681167,52.030261,4.681682,52.020860"} — into a closed
     * JTS polygon. Returns {@code null} when the string is blank, malformed, or describes
     * fewer than three points, so the caller falls back to a circle.
     */
    private static Polygon parsePolygon(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.trim().split(",");
        if (parts.length < 6 || parts.length % 2 != 0) {
            return null;
        }
        try {
            int pointCount = parts.length / 2;
            Coordinate[] coords = new Coordinate[pointCount + 1];
            for (int i = 0; i < pointCount; i++) {
                double lng = Double.parseDouble(parts[2 * i].trim());
                double lat = Double.parseDouble(parts[2 * i + 1].trim());
                coords[i] = new Coordinate(lng, lat);
            }
            coords[pointCount] = coords[0]; // close the ring
            return GEOMETRY_FACTORY.createPolygon(coords);
        } catch (IllegalArgumentException e) {
            // NumberFormatException (bad number) or an invalid ring — fall back to a circle.
            return null;
        }
    }

    private static Optional<Checkpoint> matchCheckpointByLocation(EventCheckpoint detail, List<Checkpoint> existingCheckpoints) {
        if (detail.getLocation() == null) {
            return Optional.empty();
        }
        double lat = detail.getLocation().getLatitude();
        double lng = detail.getLocation().getLongitude();

        return existingCheckpoints.stream()
            .filter(cp -> cp.getLatitude() != null && cp.getLongitude() != null)
            .map(cp -> new CheckpointDistance(cp, haversineMeters(lat, lng, cp.getLatitude(), cp.getLongitude())))
            .filter(candidate -> candidate.distanceMeters() <= CHECKPOINT_MATCH_TOLERANCE_METERS)
            .min(Comparator
                .comparingDouble(CheckpointDistance::distanceMeters)
                .thenComparing(candidate -> candidate.checkpoint().getId().toString()))
            .map(CheckpointDistance::checkpoint);
    }

    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private record CheckpointDistance(Checkpoint checkpoint, double distanceMeters) {
    }
}
