// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the secrets migration job.
 * 
 * <p>This class binds configuration properties from the application YAML file
 * under the prefix {@code secrets.backend.migration}. It provides configuration
 * for the scheduled job that re-encrypts secrets with the current key version
 * during key rotation.
 * 
 * <p><b>Configuration Example:</b>
 * <pre>
 * secrets:
 *   backend:
 *     migration:
 *       enabled: true
 *       batch-size: 100
 *       schedule: "0 0 2 * * ?"
 * </pre>
 * 
 * <p><b>Use Cases:</b>
 * <ul>
 *   <li>Re-encrypting secrets after key rotation</li>
 *   <li>Migrating plaintext secrets to encrypted format</li>
 *   <li>Ensuring all secrets use the current key version</li>
 * </ul>
 * 
 * <p><b>Performance Considerations:</b>
 * The batch size controls how many environments are processed in each database
 * query. Larger batch sizes improve throughput but increase memory usage and
 * transaction duration. The default of 100 provides a good balance for most
 * deployments.
 * 
 * <p><b>Scheduling:</b>
 * The default schedule runs daily at 2 AM (server time) to minimize impact on
 * production traffic. Adjust the cron expression based on your deployment's
 * usage patterns and maintenance windows.
 * 
 * @see org.sifunity.broker.environment.secrets.migration.MigrationJob
 * @since 1.0
 */
@Data
@ConfigurationProperties(prefix = "secrets.backend.migration")
public class MigrationProperties {
    
    /**
     * Enable or disable the migration job.
     * 
     * <p>When enabled, the migration job runs on the configured schedule to
     * re-encrypt secrets with the current key version. When disabled, no
     * automatic migration occurs.
     * 
     * <p><b>Default:</b> {@code false}
     * 
     * <p><b>When to enable:</b>
     * <ul>
     *   <li>After adding a new encryption key and updating currentVersion</li>
     *   <li>When migrating from plaintext to encrypted backend</li>
     *   <li>During key rotation to re-encrypt with the new key</li>
     * </ul>
     * 
     * <p><b>When to disable:</b>
     * <ul>
     *   <li>In development environments using plaintext backend</li>
     *   <li>After migration is complete and all secrets use current key</li>
     *   <li>When using Vault backend (Vault handles rotation internally)</li>
     * </ul>
     */
    private boolean enabled = false;
    
    /**
     * Number of environments to process in each batch.
     * 
     * <p>The migration job queries environments in batches to avoid loading
     * all environments into memory at once. Each batch is processed in a
     * separate database transaction.
     * 
     * <p><b>Default:</b> {@code 100}
     * 
     * <p><b>Tuning Guidelines:</b>
     * <ul>
     *   <li>Smaller batches (10-50): Lower memory usage, more database queries</li>
     *   <li>Medium batches (100-500): Balanced performance (recommended)</li>
     *   <li>Larger batches (500+): Higher throughput, increased memory usage</li>
     * </ul>
     * 
     * <p><b>Considerations:</b>
     * <ul>
     *   <li>Database connection pool size</li>
     *   <li>Available heap memory</li>
     *   <li>Transaction timeout settings</li>
     *   <li>Total number of environments</li>
     * </ul>
     */
    private int batchSize = 100;
    
    /**
     * Cron expression for migration job schedule.
     * Defaults to daily at 2:00 AM.
     */
    private String schedule = "0 0 2 * * ?";
}
