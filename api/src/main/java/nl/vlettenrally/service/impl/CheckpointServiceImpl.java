package nl.vlettenrally.service.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.Checkpoint;
import nl.vlettenrally.data.domain.CircleCheckpoint;
import nl.vlettenrally.data.domain.PolygonCheckpoint;
import nl.vlettenrally.data.dto.game.CheckpointRequest;
import nl.vlettenrally.data.repository.CheckpointRepository;
import nl.vlettenrally.service.CheckpointService;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of CheckpointService
 */
@ApplicationScoped
public class CheckpointServiceImpl implements CheckpointService {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Inject
    CheckpointRepository checkpointRepository;

    @Override
    public Optional<Checkpoint> findById(UUID id) {
        return checkpointRepository.findById(id);
    }

    @Override
    public List<Checkpoint> findAll() {
        return checkpointRepository.findAll();
    }

    @Override
    public List<Checkpoint> findByGameId(UUID gameId) {
        return checkpointRepository.findByGameId(gameId);
    }

    @Override
    public Optional<Checkpoint> findByGameIdAndExternalId(UUID gameId, String externalId) {
        return checkpointRepository.findByGameIdAndExternalId(gameId, externalId);
    }

    @Override
    public Checkpoint save(Checkpoint checkpoint) {
        return checkpointRepository.save(checkpoint);
    }

    @Override
    public void delete(UUID id) {
        checkpointRepository.delete(id);
    }

    @Override
    public Checkpoint create(UUID gameId, CheckpointRequest request) {
        Checkpoint checkpoint;
        if ("polygon".equals(request.zoneType()) && request.polygonPoints() != null && request.polygonPoints().size() >= 3) {
            PolygonCheckpoint poly = new PolygonCheckpoint();
            poly.setZonePolygon(toPolygon(request.polygonPoints()));
            checkpoint = poly;
        } else {
            CircleCheckpoint circle = new CircleCheckpoint();
            if (request.radiusMeters() != null) {
                circle.setRadiusMeters(request.radiusMeters());
            }
            checkpoint = circle;
        }
        checkpoint.setGameId(gameId);
        checkpoint.setName(request.name());
        checkpoint.setPoints(request.points());
        checkpoint.setLatitude(request.latitude());
        checkpoint.setLongitude(request.longitude());

        if (request.openingTime() != null) {
            checkpoint.setOpeningTime(Instant.parse(request.openingTime()));
        }

        if (request.closingTime() != null) {
            checkpoint.setClosingTime(Instant.parse(request.closingTime()));
        }

        return checkpointRepository.save(checkpoint);
    }

    @Override
    public Optional<Checkpoint> update(UUID checkpointId, CheckpointRequest request) {
        Checkpoint existing = checkpointRepository.findById(checkpointId).orElse(null);
        if (existing == null) {
            return Optional.empty();
        }

        // Determine if the zone type is changing
        boolean switchingToPolygon = "polygon".equals(request.zoneType())
            && request.polygonPoints() != null && request.polygonPoints().size() >= 3;
        boolean switchingToCircle = "circle".equals(request.zoneType()) || request.zoneType() == null;

        Checkpoint checkpoint;
        if (switchingToPolygon && !(existing instanceof PolygonCheckpoint)) {
            // Type changed from circle → polygon: create a new PolygonCheckpoint preserving common fields
            PolygonCheckpoint poly = new PolygonCheckpoint();
            copyCommonFields(existing, poly);
            poly.setZonePolygon(toPolygon(request.polygonPoints()));
            checkpoint = poly;
        } else if (switchingToCircle && !(existing instanceof CircleCheckpoint)) {
            // Type changed from polygon → circle: create a new CircleCheckpoint preserving common fields
            CircleCheckpoint circle = new CircleCheckpoint();
            copyCommonFields(existing, circle);
            if (request.radiusMeters() != null) {
                circle.setRadiusMeters(request.radiusMeters());
            }
            checkpoint = circle;
        } else {
            // Same type — update in place
            checkpoint = existing;
            if (checkpoint instanceof PolygonCheckpoint poly && request.polygonPoints() != null && request.polygonPoints().size() >= 3) {
                poly.setZonePolygon(toPolygon(request.polygonPoints()));
            } else if (checkpoint instanceof CircleCheckpoint circle && request.radiusMeters() != null) {
                circle.setRadiusMeters(request.radiusMeters());
            }
        }

        if (request.name() != null) checkpoint.setName(request.name());
        checkpoint.setPoints(request.points());
        checkpoint.setLatitude(request.latitude());
        checkpoint.setLongitude(request.longitude());
        if (request.openingTime() != null) {
            checkpoint.setOpeningTime(Instant.parse(request.openingTime()));
        }
        if (request.closingTime() != null) {
            checkpoint.setClosingTime(Instant.parse(request.closingTime()));
        }
        checkpoint.setUpdatedAt(Instant.now());

        return Optional.of(checkpointRepository.save(checkpoint));
    }

    /**
     * Copy common Checkpoint fields from one instance to another (used when zone type changes).
     */
    private void copyCommonFields(Checkpoint source, Checkpoint target) {
        target.setId(source.getId());
        target.setGameId(source.getGameId());
        target.setName(source.getName());
        target.setOpeningTime(source.getOpeningTime());
        target.setClosingTime(source.getClosingTime());
        target.setPoints(source.getPoints());
        target.setLatitude(source.getLatitude());
        target.setLongitude(source.getLongitude());
        target.setLocation(source.getLocation());
        target.setExternalId(source.getExternalId());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
    }

    /**
     * Convert a list of [lat, lng] pairs to a JTS Polygon.
     */
    private Polygon toPolygon(List<List<Double>> points) {
        // Build coordinate array: close the ring by repeating the first point
        Coordinate[] coords = new Coordinate[points.size() + 1];
        for (int i = 0; i < points.size(); i++) {
            List<Double> p = points.get(i);
            coords[i] = new Coordinate(p.get(1), p.get(0)); // JTS uses (x=lng, y=lat)
        }
        coords[points.size()] = coords[0]; // close the ring
        return GEOMETRY_FACTORY.createPolygon(coords);
    }
}
