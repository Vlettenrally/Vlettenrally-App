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
     * Return the ids of every non-hidden checkpoint in the game whose geofence zone
     * contains the given point — circle zones by radius, polygon zones by containment.
     * Resolves both shapes in a single spatial query so a fix is matched against all
     * checkpoints at once rather than one query per checkpoint.
     */
    List<UUID> findContainingPoint(UUID gameId, Point point);

    Checkpoint save(Checkpoint checkpoint);

    void delete(UUID id);
}
