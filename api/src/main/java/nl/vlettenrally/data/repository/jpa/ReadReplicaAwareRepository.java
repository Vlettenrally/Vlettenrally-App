package nl.vlettenrally.data.repository.jpa;

import io.quarkus.hibernate.orm.PersistenceUnit;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * Abstract base class for PostgreSQL repositories that provides read replica support.
 * 
 * This class injects both the primary EntityManager (for writes and consistent reads)
 * and the read-replica EntityManager (for read operations that can tolerate replication lag).
 * 
 * Subclasses should use:
 * - {@link #getReadEntityManager()} for standard read operations
 * - {@link #getReadEntityManager(boolean)} with {@code true} for reads requiring immediate consistency
 * - {@link #getWriteEntityManager()} for all write operations
 */
public abstract class ReadReplicaAwareRepository {

    @Inject
    EntityManager primaryEntityManager;

    @Inject
    @PersistenceUnit("read-replica")
    EntityManager readReplicaEntityManager;

    /**
     * Get the EntityManager for read operations.
     * Uses the read replica if available, falls back to primary if not.
     * 
     * @return EntityManager for read operations
     */
    protected EntityManager getReadEntityManager() {
        return getReadEntityManager(false);
    }

    /**
     * Get the EntityManager for read operations with explicit primary preference.
     * 
     * @param usesPrimary if true, always use the primary database for immediate consistency
     * @return EntityManager for read operations
     */
    protected EntityManager getReadEntityManager(boolean usesPrimary) {
        if (usesPrimary) {
            Log.trace("Using primary EntityManager for read (immediate consistency required)");
            return primaryEntityManager;
        }

        // Try to use read replica, fall back to primary if unavailable
        if (readReplicaEntityManager != null && isEntityManagerValid(readReplicaEntityManager)) {
            Log.trace("Using read-replica EntityManager for read");
            return readReplicaEntityManager;
        }

        Log.trace("Read-replica unavailable, falling back to primary EntityManager");
        return primaryEntityManager;
    }

    /**
     * Get the EntityManager for write operations.
     * Always uses the primary database.
     * 
     * @return EntityManager for write operations
     */
    protected EntityManager getWriteEntityManager() {
        return primaryEntityManager;
    }

    /**
     * Check if an EntityManager is valid and can be used.
     */
    private boolean isEntityManagerValid(EntityManager em) {
        try {
            return em != null && em.isOpen();
        } catch (Exception e) {
            Log.debug("EntityManager validation failed", e);
            return false;
        }
    }
}
