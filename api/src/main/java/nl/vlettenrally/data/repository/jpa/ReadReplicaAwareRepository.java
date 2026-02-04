package nl.vlettenrally.data.repository.jpa;

import io.quarkus.hibernate.orm.PersistenceUnit;
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
     * Currently uses primary for all operations to avoid transaction enlistment issues.
     * 
     * @return EntityManager for read operations
     */
    protected EntityManager getReadEntityManager() {
        return primaryEntityManager;
    }

    /**
     * Get the EntityManager for read operations with explicit primary preference.
     * 
     * @param usesPrimary if true, always use the primary database for immediate consistency
     * @return EntityManager for read operations
     */
    protected EntityManager getReadEntityManager(boolean usesPrimary) {
        // Currently always use primary to avoid transaction enlistment issues
        // TODO: Re-enable read replica support with proper transaction awareness
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
}
