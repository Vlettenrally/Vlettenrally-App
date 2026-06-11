package nl.vlettenrally.service.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import nl.vlettenrally.data.domain.Checkpoint;
import nl.vlettenrally.data.domain.CircleCheckpoint;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.PolygonCheckpoint;
import nl.vlettenrally.data.dto.game.CheckpointRequest;
import nl.vlettenrally.data.dto.game.CopyCheckpointsResult;
import nl.vlettenrally.data.repository.CheckpointRepository;
import nl.vlettenrally.service.CheckpointService;
import nl.vlettenrally.service.GameService;
import nl.vlettenrally.service.util.GameTimeShifter;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of CheckpointService
 */
@ApplicationScoped
public class CheckpointServiceImpl implements CheckpointService {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Inject
    CheckpointRepository checkpointRepository;

    @Inject
    GameService gameService;

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
        checkpoint.setHidden(request.hidden());

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
        checkpoint.setHidden(request.hidden());
        if (request.openingTime() != null) {
            checkpoint.setOpeningTime(Instant.parse(request.openingTime()));
        }
        if (request.closingTime() != null) {
            checkpoint.setClosingTime(Instant.parse(request.closingTime()));
        }
        checkpoint.setUpdatedAt(Instant.now());

        return Optional.of(checkpointRepository.save(checkpoint));
    }

    @Override
    public CopyCheckpointsResult copyCheckpoints(UUID targetGameId, UUID sourceGameId) {
        if (targetGameId.equals(sourceGameId)) {
            throw new BadRequestException("Cannot copy zones from a game into itself");
        }

        Game sourceGame = gameService.findById(sourceGameId)
            .orElseThrow(() -> new NotFoundException("Source game not found: " + sourceGameId));
        Game targetGame = gameService.findById(targetGameId)
            .orElseThrow(() -> new NotFoundException("Target game not found: " + targetGameId));

        Set<String> existingNames = checkpointRepository.findByGameId(targetGameId).stream()
            .map(cp -> cp.getName().toLowerCase(Locale.ROOT))
            .collect(Collectors.toCollection(HashSet::new));

        int copied = 0;
        List<String> skippedDuplicate = new ArrayList<>();

        for (Checkpoint source : checkpointRepository.findByGameId(sourceGameId)) {
            if (!existingNames.add(source.getName().toLowerCase(Locale.ROOT))) {
                skippedDuplicate.add(source.getName());
                continue;
            }

            Checkpoint copy;
            if (source instanceof PolygonCheckpoint poly) {
                PolygonCheckpoint p = new PolygonCheckpoint();
                p.setZonePolygon(poly.getZonePolygon());
                copy = p;
            } else {
                CircleCheckpoint c = new CircleCheckpoint();
                c.setRadiusMeters(((CircleCheckpoint) source).getRadiusMeters());
                copy = c;
            }

            copy.setGameId(targetGameId);
            copy.setName(source.getName());
            copy.setPoints(source.getPoints());
            copy.setLatitude(source.getLatitude());
            copy.setLongitude(source.getLongitude());
            copy.setLocation(source.getLocation());
            copy.setHidden(source.isHidden());
            // externalId intentionally left null: it is a tracker-sync key unique per game.
            copy.setOpeningTime(GameTimeShifter.adjust(source.getOpeningTime(), sourceGame, targetGame));
            copy.setClosingTime(GameTimeShifter.adjust(source.getClosingTime(), sourceGame, targetGame));

            checkpointRepository.save(copy);
            copied++;
        }

        return new CopyCheckpointsResult(copied, skippedDuplicate);
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
        target.setHidden(source.isHidden());
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
