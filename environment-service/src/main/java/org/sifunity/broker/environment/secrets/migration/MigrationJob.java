// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.migration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.secrets.SecretsBackend;
import org.sifunity.broker.environment.secrets.config.MigrationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background job for migrating secrets to the current key version.
 * 
 * <p>This component runs on a configurable schedule to re-encrypt secrets from old key
 * versions to the current key version. It is essential for maintaining security during
 * key rotation operations and for migrating plaintext secrets to encrypted format.
 * 
 * <p><b>Key Features:</b>
 * <ul>
 *   <li>Processes secrets in configurable batches to avoid database overload</li>
 *   <li>Continues processing even if individual secret migrations fail</li>
 *   <li>Logs detailed progress including counts of migrated and failed secrets</li>
 *   <li>Does not block API requests during migration (non-blocking operation)</li>
 *   <li>Detects and encrypts plaintext secrets (backward compatibility)</li>
 * </ul>
 * 
 * <p><b>Use Cases:</b>
 * <ul>
 *   <li><b>Key Rotation:</b> After adding a new encryption key and updating the current
 *       version, enable this job to re-encrypt all secrets with the new key</li>
 *   <li><b>Backend Migration:</b> When switching from PlaintextBackend to MultiKeyBackend,
 *       this job detects plaintext secrets and encrypts them</li>
 *   <li><b>Security Compliance:</b> Ensures all secrets use the latest encryption key
 *       version for optimal security</li>
 * </ul>
 * 
 * <p><b>Configuration:</b>
 * <pre>
 * secrets:
 *   backend:
 *     migration:
 *       enabled: true              # Enable the migration job
 *       batch-size: 100            # Number of environments per batch
 *       schedule: "0 0 2 * * ?"    # Cron expression (daily at 2 AM)
 * </pre>
 * 
 * <p><b>Conditional Activation:</b> This component is only created when the property
 * {@code secrets.backend.migration.enabled} is set to {@code true}. This allows operators
 * to enable migration only when needed (e.g., during key rotation) and disable it once
 * migration is complete.
 * 
 * <p><b>Performance Considerations:</b>
 * <ul>
 *   <li>Batch size controls memory usage and transaction duration</li>
 *   <li>Larger batches improve throughput but increase memory consumption</li>
 *   <li>Schedule during low-traffic periods to minimize impact</li>
 *   <li>Monitor job duration and adjust batch size if needed</li>
 * </ul>
 * 
 * <p><b>Error Handling:</b> Individual secret migration failures are logged but do not
 * stop the job. This ensures that one problematic secret doesn't prevent migration of
 * all other secrets. Failed secrets can be investigated and retried on the next scheduled
 * run.
 * 
 * <p><b>Thread Safety:</b> This job is designed to run as a single scheduled task. If
 * multiple instances of the application are running, ensure that only one instance has
 * migration enabled to avoid concurrent migration attempts.
 * 
 * @see MigrationProperties
 * @see SecretsBackend
 * @see org.sifunity.broker.environment.secrets.impl.MultiKeyBackend
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "secrets.backend.migration.enabled",
    havingValue = "true"
)
public class MigrationJob {
    
    /**
     * The secrets backend used for retrieving and storing secrets.
     * 
     * <p>During migration, secrets are retrieved using the old key version (if applicable)
     * and stored using the current key version. The backend handles the encryption/decryption
     * logic transparently.
     */
    private final SecretsBackend secretsBackend;
    
    /**
     * Repository for accessing environment entities.
     * 
     * <p>Used to query environments in batches during migration. The repository provides
     * pagination support to process large numbers of environments efficiently.
     */
    private final EnvironmentRepository environmentRepository;
    
    /**
     * Configuration properties for the migration job.
     * 
     * <p>Contains settings for batch size, schedule, and other migration parameters.
     * These properties are loaded from the application configuration file.
     */
    private final MigrationProperties properties;
    
    /**
     * Timestamp of the last migration job execution.
     */
    private volatile Instant lastRun;
    
    /**
     * Total number of environments processed in the last run.
     */
    private volatile int totalEnvironments;
    
    /**
     * Number of environments successfully migrated in the last run.
     */
    private volatile int migrated;
    
    /**
     * Number of environments that failed to migrate in the last run.
     */
    private volatile int failed;
    
    /**
     * Whether a migration job is currently in progress.
     */
    private final AtomicBoolean inProgress = new AtomicBoolean(false);


    /**
     * Scheduled method that migrates secrets to the current key version.
     *
     * <p>This method runs on the schedule defined in {@link MigrationProperties#getSchedule()}.
     * It processes all environments in batches, re-encrypting their secrets with the current
     * key version. The method is designed to be resilient to individual failures and provides
     * detailed progress logging.
     *
     * <p><b>Processing Flow:</b>
     * <ol>
     *   <li>Log job start</li>
     *   <li>Initialize counters for migrated and failed secrets</li>
     *   <li>Query environments in pages using configured batch size</li>
     *   <li>For each environment, attempt migration with error handling</li>
     *   <li>Log progress after each batch</li>
     *   <li>Log final summary when complete</li>
     * </ol>
     *
     * <p><b>Error Handling:</b> If migration fails for an individual environment, the error
     * is logged and the job continues with the next environment. This ensures that one
     * problematic secret doesn't prevent migration of all other secrets.
     *
     * <p><b>Performance:</b> The method uses Spring Data pagination to process environments
     * in batches, avoiding loading all environments into memory at once. This allows the
     * job to scale to large numbers of environments.
     *
     * <p><b>Monitoring:</b> Progress is logged after each batch, allowing operators to
     * monitor migration status in real-time. The final summary provides total counts of
     * migrated and failed secrets.
     *
     * @see MigrationProperties#getSchedule()
     * @see MigrationProperties#getBatchSize()
     */
    @Scheduled(cron = "${secrets.backend.migration.schedule:0 0 2 * * ?}")
    public void migrateSecrets() {
        if (!inProgress.compareAndSet(false, true)) {
            log.warn("Migration job already in progress, skipping this execution");
            return;
        }
        
        try {
            log.info("Starting secrets migration job");

            int totalMigrated = 0;
            int totalFailed = 0;
            int totalEnvs = 0;

            int batchSize = properties.getBatchSize();
            int page = 0;

            Page<Environment> environments;

            do {
                environments = environmentRepository.findAll(
                    Pageable.ofSize(batchSize).withPage(page)
                );

                totalEnvs += environments.getNumberOfElements();

                for (Environment env : environments.getContent()) {
                    try {
                        migrateEnvironmentSecret(env);
                        totalMigrated++;
                    } catch (Exception e) {
                        log.error("Failed to migrate secret for environment {}", env.getId(), e);
                        totalFailed++;
                    }
                }

                log.info("Migration progress: {} migrated, {} failed", totalMigrated, totalFailed);

                page++;
            } while (environments.hasNext());

            log.info("Migration job complete: {} migrated, {} failed", totalMigrated, totalFailed);
            
            // Update status
            this.lastRun = Instant.now();
            this.totalEnvironments = totalEnvs;
            this.migrated = totalMigrated;
            this.failed = totalFailed;
        } finally {
            inProgress.set(false);
        }
    }
    
    /**
     * Returns the current migration status.
     * 
     * <p>This method is called by the MigrationController to expose status via REST API.
     * 
     * @return the current migration status
     */
    public MigrationStatus getStatus() {
        MigrationStatus status = new MigrationStatus();
        status.setLastRun(lastRun);
        status.setTotalEnvironments(totalEnvironments);
        status.setMigrated(migrated);
        status.setFailed(failed);
        status.setCurrentKeyVersion(getCurrentKeyVersion());
        status.setInProgress(inProgress.get());
        return status;
    }
    
    /**
     * Gets the current key version from the secrets backend.
     * 
     * @return the current key version, or "N/A" if not applicable
     */
    private String getCurrentKeyVersion() {
        String backendType = secretsBackend.getBackendType();
        if ("multi-key".equals(backendType)) {
            // For multi-key backend, we would need to expose the current version
            // For now, return the backend type
            return backendType;
        }
        return backendType;
    }

    /**
     * Migrates a single environment's secret to the current key version.
     *
     * <p>This method retrieves the secret using the backend (which handles decryption
     * with the old key version if applicable) and stores it back (which encrypts with
     * the current key version). This effectively re-encrypts the secret.
     *
     * <p><b>Key Rotation:</b> When a new encryption key is configured as the current
     * version, this method ensures secrets are re-encrypted with the new key.
     *
     * <p><b>Plaintext Migration:</b> If the backend detects a plaintext secret (no
     * version prefix), it will encrypt it with the current key version.
     *
     * @param env the environment whose secret should be migrated
     * @throws Exception if migration fails (caught by caller)
     */
    private void migrateEnvironmentSecret(Environment env) {
        String secret = secretsBackend.retrieve(env.getId());
        secretsBackend.store(env.getId(), secret);
    }

}
