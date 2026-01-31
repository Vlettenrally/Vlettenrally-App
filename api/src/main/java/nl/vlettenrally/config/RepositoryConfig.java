package nl.vlettenrally.config;

/**
 * Documentation for repository configuration.
 * 
 * Repository implementations are selected automatically based on Quarkus build profile:
 * 
 * - In dev/test profiles: Mock implementations (from nl.vlettenrally.data.repository.mock) are used.
 *   These are annotated with @IfBuildProfile(anyOf = {"dev", "test"})
 * 
 * - In prod profile: JPA implementations (from nl.vlettenrally.data.repository.jpa) are used.
 *   These are annotated with @IfBuildProfile("prod")
 * 
 * Required environment variables for production:
 * <ul>
 *   <li>DB_HOST - Primary database host (required)</li>
 *   <li>DB_NAME - Database name (defaults to 'vlettenrally')</li>
 *   <li>DB_USER - Database username (required)</li>
 *   <li>DB_PASSWORD - Database password (required)</li>
 *   <li>DB_READ_HOST - Read replica host (optional, defaults to DB_HOST)</li>
 * </ul>
 * 
 * Example Kubernetes secret mount:
 * <pre>
 * env:
 *   - name: DB_HOST
 *     valueFrom:
 *       secretKeyRef:
 *         name: postgres-credentials
 *         key: host
 *   - name: DB_USER
 *     valueFrom:
 *       secretKeyRef:
 *         name: postgres-credentials
 *         key: username
 *   - name: DB_PASSWORD
 *     valueFrom:
 *       secretKeyRef:
 *         name: postgres-credentials
 *         key: password
 *   - name: DB_READ_HOST
 *     valueFrom:
 *       secretKeyRef:
 *         name: postgres-credentials
 *         key: read-host
 *         optional: true
 * </pre>
 * 
 * The @IfBuildProfile annotation ensures beans are only instantiated for the correct profile,
 * avoiding the need for @Alternative or @DefaultBean annotations.
 */
public final class RepositoryConfig {
    private RepositoryConfig() {
        // Documentation-only class
    }
}
