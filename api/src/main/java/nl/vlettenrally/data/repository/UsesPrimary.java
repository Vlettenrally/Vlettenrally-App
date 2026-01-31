package nl.vlettenrally.data.repository;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation for repository methods that require immediate consistency
 * and should read from the primary database instead of the read replica.
 * 
 * Use this annotation on read methods that are called immediately after a write
 * operation where replication lag could cause stale data to be returned.
 * 
 * Example usage:
 * <pre>
 * {@literal @}UsesPrimary
 * public Optional&lt;Team&gt; findById(UUID id) {
 *     // This will use the primary database
 * }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface UsesPrimary {
    /**
     * Optional description explaining why this method requires primary database access.
     */
    String reason() default "";
}
