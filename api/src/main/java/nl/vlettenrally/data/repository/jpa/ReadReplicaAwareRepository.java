package nl.vlettenrally.data.repository.jpa;

import io.quarkus.hibernate.orm.PersistenceUnit;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Status;
import jakarta.transaction.TransactionSynchronizationRegistry;

/**
 * Abstract base class for PostgreSQL repositories that provides read replica support.
 * <p>
 * This class injects both the primary EntityManager (for writes and consistent reads)
 * and the read-replica EntityManager (for read operations that can tolerate replication lag).
 * <p>
 * Reads are routed to the replica only when there is no active JTA transaction. When a
 * transaction is active (a write flow, or any {@code @Transactional} method), reads go to
 * the primary. This keeps a second datasource from ever being enlisted into an active
 * transaction — the enlistment conflict that previously forced all reads onto the primary —
 * and gives read-your-writes consistency for free within a transaction.
 * <p>
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

    @Inject
    TransactionSynchronizationRegistry txRegistry;

    /**
     * Whether a JTA transaction is active (or mid commit/rollback) on the current thread.
     * Only a clean "no transaction" state is safe for replica routing.
     */
    private boolean transactionActive() {
        return txRegistry.getTransactionStatus() != Status.STATUS_NO_TRANSACTION;
    }

    /**
     * Get the EntityManager for read operations.
     * Uses the read replica when no transaction is active, otherwise the primary.
     *
     * @return EntityManager for read operations
     */
    protected EntityManager getReadEntityManager() {
        return transactionActive() ? primaryEntityManager : readReplicaEntityManager;
    }

    /**
     * Get the EntityManager for read operations with explicit primary preference.
     *
     * @param usesPrimary if true, always use the primary database for immediate consistency
     * @return EntityManager for read operations
     */
    protected EntityManager getReadEntityManager(boolean usesPrimary) {
        if (usesPrimary) {
            return primaryEntityManager;
        }
        return getReadEntityManager();
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
