package nl.vlettenrally.data.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import nl.vlettenrally.data.domain.Checkpoint;
import org.locationtech.jts.geom.Point;

/**
 * Repository interface for Checkpoint entities
 */
public interface CheckpointRepository {

    Optional<Checkpoint> findById(UUID id);

    List<Checkpoint> findAll();

    List<Checkpoint> findByGameId(UUID gameId);

    Optional<Checkpoint> findByGameIdAndExternalId(UUID gameId, String externalId);

    /**
     * Return the ids of every checkpoint in the game whose geofence zone
     * contains the given point — circle zones by radius, polygon zones by containment.
     * Resolves both shapes in a single spatial query so a fix is matched against all
     * checkpoints at once rather than one query per checkpoint. Hidden checkpoints are
     * included: they drive automations even though they never appear on teams' map.
     */
    List<UUID> findContainingPoint(UUID gameId, Point point);

    /**
     * Return the ids of every checkpoint in the game whose geofence zone is
     * crossed by the straight segment a→b — circle zones whose centre lies within
     * radius_meters of the line, polygon zones the line intersects. A strict superset of
     * {@link #findContainingPoint} for the endpoint {@code b}: used to credit a pass when a
     * boat transits a zone between two consecutive fixes without either fix landing inside.
     */
    List<UUID> findCrossedBySegment(UUID gameId, Point a, Point b);

    Checkpoint save(Checkpoint checkpoint);

    void delete(UUID id);
}
