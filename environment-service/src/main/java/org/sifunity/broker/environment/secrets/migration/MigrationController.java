// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.migration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for migration job monitoring and manual triggering.
 * 
 * <p>This controller provides endpoints for operators to:
 * <ul>
 *   <li>Check the current migration status (last run, counts, progress)</li>
 *   <li>Manually trigger a migration job on-demand</li>
 * </ul>
 * 
 * <p><b>Endpoints:</b>
 * <ul>
 *   <li><b>GET /api/migration/status</b> - Returns current migration status including
 *       last run time, total environments, migrated count, failed count, current key
 *       version, and whether migration is in progress</li>
 *   <li><b>POST /api/migration/trigger</b> - Manually triggers a migration job and
 *       returns the updated status</li>
 * </ul>
 * 
 * <p><b>Use Cases:</b>
 * <ul>
 *   <li><b>Monitoring:</b> Check migration progress and results after key rotation</li>
 *   <li><b>Manual Trigger:</b> Run migration immediately after configuration changes
 *       instead of waiting for the scheduled execution</li>
 *   <li><b>Troubleshooting:</b> Verify migration status when investigating issues</li>
 * </ul>
 * 
 * <p><b>Conditional Activation:</b> This controller is only created when the property
 * {@code secrets.backend.migration.enabled} is set to {@code true}. This ensures the
 * endpoints are only available when migration functionality is enabled.
 * 
 * <p><b>Security Considerations:</b> These endpoints should be protected with appropriate
 * authentication and authorization in production environments, as they expose operational
 * information and allow triggering potentially resource-intensive operations.
 * 
 * @see MigrationJob
 * @see MigrationStatus
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/migration")
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "secrets.backend.migration.enabled",
    havingValue = "true"
)
public class MigrationController {
    
    /**
     * The migration job that performs secret re-encryption.
     * 
     * <p>This controller delegates to the MigrationJob for both status retrieval
     * and manual triggering.
     */
    private final MigrationJob migrationJob;
    
    /**
     * Returns the current migration status.
     * 
     * <p>This endpoint provides visibility into the migration job's execution history
     * and current state. It returns information about the last run, including:
     * <ul>
     *   <li>Timestamp of last execution</li>
     *   <li>Total number of environments processed</li>
     *   <li>Number of successfully migrated environments</li>
     *   <li>Number of failed migrations</li>
     *   <li>Current key version being used</li>
     *   <li>Whether a migration is currently in progress</li>
     * </ul>
     * 
     * <p><b>Example Response:</b>
     * <pre>
     * {
     *   "lastRun": "2024-01-15T02:00:00Z",
     *   "totalEnvironments": 150,
     *   "migrated": 148,
     *   "failed": 2,
     *   "currentKeyVersion": "20240115",
     *   "inProgress": false
     * }
     * </pre>
     * 
     * @return ResponseEntity containing the current MigrationStatus
     */
    @GetMapping("/status")
    public ResponseEntity<MigrationStatus> getStatus() {
        log.debug("Migration status requested");
        MigrationStatus status = migrationJob.getStatus();
        return ResponseEntity.ok(status);
    }
    
    /**
     * Manually triggers a migration job execution.
     * 
     * <p>This endpoint allows operators to run the migration job on-demand instead of
     * waiting for the scheduled execution. This is useful when:
     * <ul>
     *   <li>A new encryption key has been configured and immediate migration is desired</li>
     *   <li>The backend has been switched and secrets need to be migrated</li>
     *   <li>Previous migration had failures that need to be retried</li>
     * </ul>
     * 
     * <p><b>Behavior:</b>
     * <ul>
     *   <li>If no migration is in progress, starts a new migration immediately</li>
     *   <li>If a migration is already in progress, returns the current status without
     *       starting a new migration</li>
     *   <li>Returns the updated status after triggering (or current status if already running)</li>
     * </ul>
     * 
     * <p><b>Performance Note:</b> Migration can be resource-intensive for large numbers
     * of environments. Consider the current system load before triggering manually.
     * 
     * @return ResponseEntity containing the MigrationStatus after triggering
     */
    @PostMapping("/trigger")
    public ResponseEntity<MigrationStatus> triggerMigration() {
        log.info("Manual migration trigger requested");
        
        MigrationStatus currentStatus = migrationJob.getStatus();
        
        if (currentStatus.isInProgress()) {
            log.warn("Migration already in progress, returning current status");
            return ResponseEntity.ok(currentStatus);
        }
        
        // Trigger migration in the current thread (synchronous)
        // This ensures the caller gets the updated status
        migrationJob.migrateSecrets();
        
        MigrationStatus updatedStatus = migrationJob.getStatus();
        log.info("Manual migration completed: {} migrated, {} failed", 
            updatedStatus.getMigrated(), updatedStatus.getFailed());
        
        return ResponseEntity.ok(updatedStatus);
    }
}
