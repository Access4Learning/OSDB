// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.migration;

import lombok.Data;

import java.time.Instant;

/**
 * Model representing the current status of the secrets migration job.
 * 
 * <p>This status is returned by the migration status endpoint to provide
 * visibility into migration progress and results.
 */
@Data
public class MigrationStatus {
    
    /**
     * Timestamp of the last migration job execution.
     */
    private Instant lastRun;
    
    /**
     * Total number of environments in the database.
     */
    private int totalEnvironments;
    
    /**
     * Number of environments successfully migrated in the last run.
     */
    private int migrated;
    
    /**
     * Number of environments that failed to migrate in the last run.
     */
    private int failed;
    
    /**
     * Current key version being used for new encryptions.
     */
    private String currentKeyVersion;
    
    /**
     * Whether a migration job is currently in progress.
     */
    private boolean inProgress;
}
