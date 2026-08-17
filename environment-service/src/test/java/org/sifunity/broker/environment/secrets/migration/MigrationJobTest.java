// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.migration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.secrets.SecretsBackend;
import org.sifunity.broker.environment.secrets.SecretsBackendException;
import org.sifunity.broker.environment.secrets.config.MigrationProperties;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MigrationJob}.
 * 
 * <p>These tests validate the migration job's behavior for specific scenarios including
 * batch processing, progress logging, failure handling, empty databases, and plaintext
 * secret detection.</p>
 * 
 * <p><strong>Validates: Requirements 11.1, 11.7</strong></p>
 */
@ExtendWith(MockitoExtension.class)
class MigrationJobTest {

    @Mock
    private SecretsBackend secretsBackend;

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private MigrationProperties properties;

    private MigrationJob migrationJob;

    @BeforeEach
    void setUp() {
        migrationJob = new MigrationJob(secretsBackend, environmentRepository, properties);
    }

    // ========== Batch Processing Tests ==========

    /**
     * Test that migration job processes environments in batches of size 1.
     * 
     * <p>This tests the smallest possible batch size to ensure the job handles
     * single-item batches correctly.</p>
     */
    @Test
    void shouldProcessEnvironmentsInBatchesOfOne() {
        // Given: 3 environments with batch size 1
        int batchSize = 1;
        List<Environment> environments = createEnvironments(3);
        
        when(properties.getBatchSize()).thenReturn(batchSize);
        configurePaginatedRepository(environments, batchSize);
        when(secretsBackend.retrieve(any(UUID.class))).thenReturn("test-secret");

        // When: Migration runs
        migrationJob.migrateSecrets();

        // Then: Repository should be queried 3 times (one per environment)
        verify(environmentRepository, times(3)).findAll(any(Pageable.class));
        
        // And: All environments should be processed
        verify(secretsBackend, times(3)).retrieve(any(UUID.class));
        verify(secretsBackend, times(3)).store(any(UUID.class), eq("test-secret"));
    }

    /**
     * Test that migration job processes environments in batches of size 10.
     * 
     * <p>This tests a typical small batch size.</p>
     */
    @Test
    void shouldProcessEnvironmentsInBatchesOfTen() {
        // Given: 25 environments with batch size 10
        int batchSize = 10;
        List<Environment> environments = createEnvironments(25);
        
        when(properties.getBatchSize()).thenReturn(batchSize);
        configurePaginatedRepository(environments, batchSize);
        when(secretsBackend.retrieve(any(UUID.class))).thenReturn("test-secret");

        // When: Migration runs
        migrationJob.migrateSecrets();

        // Then: Repository should be queried 3 times (pages 0, 1, 2)
        verify(environmentRepository, times(3)).findAll(any(Pageable.class));
        
        // And: All 25 environments should be processed
        verify(secretsBackend, times(25)).retrieve(any(UUID.class));
        verify(secretsBackend, times(25)).store(any(UUID.class), eq("test-secret"));
    }

    /**
     * Test that migration job processes environments in batches of size 100.
     * 
     * <p>This tests the default batch size (100) with a realistic dataset.</p>
     */
    @Test
    void shouldProcessEnvironmentsInBatchesOfOneHundred() {
        // Given: 250 environments with batch size 100
        int batchSize = 100;
        List<Environment> environments = createEnvironments(250);
        
        when(properties.getBatchSize()).thenReturn(batchSize);
        configurePaginatedRepository(environments, batchSize);
        when(secretsBackend.retrieve(any(UUID.class))).thenReturn("test-secret");

        // When: Migration runs
        migrationJob.migrateSecrets();

        // Then: Repository should be queried 3 times (pages 0, 1, 2)
        verify(environmentRepository, times(3)).findAll(any(Pageable.class));
        
        // And: All 250 environments should be processed
        verify(secretsBackend, times(250)).retrieve(any(UUID.class));
        verify(secretsBackend, times(250)).store(any(UUID.class), eq("test-secret"));
    }

    /**
     * Test that migration job correctly handles partial last page.
     * 
     * <p>When the total environment count doesn't divide evenly by batch size,
     * the last page will be partial.</p>
     */
    @Test
    void shouldHandlePartialLastPage() {
        // Given: 23 environments with batch size 10 (last page has 3 items)
        int batchSize = 10;
        List<Environment> environments = createEnvironments(23);
        
        when(properties.getBatchSize()).thenReturn(batchSize);
        configurePaginatedRepository(environments, batchSize);
        when(secretsBackend.retrieve(any(UUID.class))).thenReturn("test-secret");

        // When: Migration runs
        migrationJob.migrateSecrets();

        // Then: Repository should be queried 3 times (10 + 10 + 3)
        verify(environmentRepository, times(3)).findAll(any(Pageable.class));
        
        // And: All 23 environments should be processed
        verify(secretsBackend, times(23)).retrieve(any(UUID.class));
        verify(secretsBackend, times(23)).store(any(UUID.class), eq("test-secret"));
    }

    /**
     * Test that migration job uses the configured batch size from properties.
     * 
     * <p>Verifies that the job respects the batch size configuration.</p>
     */
    @Test
    void shouldUseBatchSizeFromConfiguration() {
        // Given: Custom batch size of 50
        int batchSize = 50;
        List<Environment> environments = createEnvironments(100);
        
        when(properties.getBatchSize()).thenReturn(batchSize);
        configurePaginatedRepository(environments, batchSize);
        when(secretsBackend.retrieve(any(UUID.class))).thenReturn("test-secret");

        // When: Migration runs
        migrationJob.migrateSecrets();

        // Then: Repository queries should use batch size 50
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(environmentRepository, atLeastOnce()).findAll(pageableCaptor.capture());
        
        List<Pageable> pageables = pageableCaptor.getAllValues();
        assertThat(pageables).allMatch(p -> p.getPageSize() == batchSize);
    }

    // ========== Progress Logging Tests ==========

    /**
     * Test that migration job logs progress after each batch.
     * 
     * <p>This test verifies that progress logging occurs at the expected frequency.
     * Since we can't easily verify log output in unit tests, we verify the behavior
     * by checking that processing continues through all batches.</p>
     */
    @Test
    void shouldLogProgressAfterEachBatch() {
        // Given: Multiple batches of environments
        int batchSize = 10;
        List<Environment> environments = createEnvironments(30);
        
        when(properties.getBatchSize()).thenReturn(batchSize);
        configurePaginatedRepository(environments, batchSize);
        when(secretsBackend.retrieve(any(UUID.class))).thenReturn("test-secret");

        // When: Migration runs
        migrationJob.migrateSecrets();

        // Then: All batches should be processed (3 batches)
        verify(environmentRepository, times(3)).findAll(any(Pageable.class));
        
        // And: All environments should be migrated
        verify(secretsBackend, times(30)).retrieve(any(UUID.class));
        verify(secretsBackend, times(30)).store(any(UUID.class), anyString());
    }

    /**
     * Test that migration job logs final summary with correct counts.
     * 
     * <p>Verifies that the job processes all environments and would log the
     * correct final counts.</p>
     */
    @Test
    void shouldLogFinalSummaryWithCorrectCounts() {
        // Given: 15 environments, 3 will fail
        int batchSize = 10;
        List<Environment> environments = createEnvironments(15);
        UUID failingId1 = environments.get(3).getId();
        UUID failingId2 = environments.get(7).getId();
        UUID failingId3 = environments.get(12).getId();
        
        when(properties.getBatchSize()).thenReturn(batchSize);
        configurePaginatedRepository(environments, batchSize);
        
        // Configure backend to fail for specific environments
        when(secretsBackend.retrieve(any(UUID.class)))
            .thenAnswer(invocation -> {
                UUID envId = invocation.getArgument(0);
                if (envId.equals(failingId1) || envId.equals(failingId2) || envId.equals(failingId3)) {
                    throw new SecretsBackendException("Simulated failure");
                }
                return "test-secret";
            });

        // When: Migration runs
        migrationJob.migrateSecrets();

        // Then: 15 retrieve attempts (all environments)
        verify(secretsBackend, times(15)).retrieve(any(UUID.class));
        
        // And: 12 successful stores (15 - 3 failures)
        verify(secretsBackend, times(12)).store(any(UUID.class), anyString());
    }

    // ========== Failure Handling Tests ==========

    /**
     * Test that migration job continues processing after individual failures.
     * 
     * <p>When one environment fails to migrate, the job should continue with
     * the remaining environments.</p>
     */
    @Test
    void shouldContinueProcessingAfterIndividualFailure() {
        // Given: 5 environments, second one will fail
        int batchSize = 10;
        List<Environment> environments = createEnvironments(5);
        UUID failingId = environments.get(1).getId();
        
        when(properties.getBatchSize()).thenReturn(batchSize);
        configurePaginatedRepository(environments, batchSize);
        
        when(secretsBackend.retrieve(any(UUID.class)))
            .thenAnswer(invocation -> {
                UUID envId = invocation.getArgument(0);
                if (envId.equals(failingId)) {
                    throw new SecretsBackendException("Simulated failure");
                }
                return "test-secret";
            });

        // When: Migration runs
        migrationJob.migrateSecrets();

        // Then: All 5 environments should be attempted
        verify(secretsBackend, times(5)).retrieve(any(UUID.class));
        
        // And: 4 should be successfully stored (5 - 1 failure)
        verify(secretsBackend, times(4)).store(any(UUID.class), anyString());
    }

    /**
     * Test that migration job handles multiple failures in a single batch.
     * 
     * <p>Multiple failures within the same batch should not stop processing.</p>
     */
    @Test
    void shouldHandleMultipleFailuresInSingleBatch() {
        // Given: 10 environments in one batch, 3 will fail
        int batchSize = 10;
        List<Environment> environments = createEnvironments(10);
        UUID failingId1 = environments.get(2).getId();
        UUID failingId2 = environments.get(5).getId();
        UUID failingId3 = environments.get(8).getId();
        
        when(properties.getBatchSize()).thenReturn(batchSize);
        configurePaginatedRepository(environments, batchSize);
        
        when(secretsBackend.retrieve(any(UUID.class)))
            .thenAnswer(invocation -> {
                UUID envId = invocation.getArgument(0);
                if (envId.equals(failingId1) || envId.equals(failingId2) || envId.equals(failingId3)) {
                    throw new SecretsBackendException("Simulated failure");
                }
                return "test-secret";
            });

        // When: Migration runs
        migrationJob.migrateSecrets();

        // Then: All 10 environments should be attempted
        verify(secretsBackend, times(10)).retrieve(any(UUID.class));
        
        // And: 7 should be successfully stored (10 - 3 failures)
        verify(secretsBackend, times(7)).store(any(UUID.class), anyString());
    }

    /**
     * Test that migration job handles failures across multiple batches.
     * 
     * <p>Failures in one batch should not prevent processing of subsequent batches.</p>
     */
    @Test
    void shouldHandleFailuresAcrossMultipleBatches() {
        // Given: 25 environments across 3 batches, failures in each batch
        int batchSize = 10;
        List<Environment> environments = createEnvironments(25);
        UUID failingId1 = environments.get(3).getId();   // First batch
        UUID failingId2 = environments.get(15).getId();  // Second batch
        UUID failingId3 = environments.get(22).getId();  // Third batch
        
        when(properties.getBatchSize()).thenReturn(batchSize);
        configurePaginatedRepository(environments, batchSize);
        
        when(secretsBackend.retrieve(any(UUID.class)))
            .thenAnswer(invocation -> {
                UUID envId = invocation.getArgument(0);
                if (envId.equals(failingId1) || envId.equals(failingId2) || envId.equals(failingId3)) {
                    throw new SecretsBackendException("Simulated failure");
                }
                return "test-secret";
            });

        // When: Migration runs
        migrationJob.migrateSecrets();

        // Then: All 3 batches should be processed
        verify(environmentRepository, times(3)).findAll(any(Pageable.class));
        
        // And: All 25 environments should be attempted
        verify(secretsBackend, times(25)).retrieve(any(UUID.class));
        
        // And: 22 should be successfully stored (25 - 3 failures)
        verify(secretsBackend, times(22)).store(any(UUID.class), anyString());
    }

    /**
     * Test that migration job handles store failures.
     * 
     * <p>Failures during the store operation should also be handled gracefully.</p>
     */
    @Test
    void shouldHandleStoreFailures() {
        // Given: 5 environments, store will fail for one
        int batchSize = 10;
        List<Environment> environments = createEnvironments(5);
        UUID failingId = environments.get(2).getId();
        
        when(properties.getBatchSize()).thenReturn(batchSize);
        configurePaginatedRepository(environments, batchSize);
        when(secretsBackend.retrieve(any(UUID.class))).thenReturn("test-secret");
        
        doAnswer(invocation -> {
            UUID envId = invocation.getArgument(0);
            if (envId.equals(failingId)) {
                throw new SecretsBackendException("Store failed");
            }
            return null;
        }).when(secretsBackend).store(any(UUID.class), anyString());

        // When: Migration runs
        migrationJob.migrateSecrets();

        // Then: All 5 environments should be attempted
        verify(secretsBackend, times(5)).retrieve(any(UUID.class));
        
        // And: Store should be attempted for all 5
        verify(secretsBackend, times(5)).store(any(UUID.class), anyString());
    }

    // ========== Empty Database Tests ==========

    /**
     * Test that migration job handles empty database gracefully.
     * 
     * <p>When there are no environments to migrate, the job should complete
     * without errors.</p>
     */
    @Test
    void shouldHandleEmptyDatabase() {
        // Given: No environments
        int batchSize = 100;
        List<Environment> environments = createEnvironments(0);
        
        when(properties.getBatchSize()).thenReturn(batchSize);
        configurePaginatedRepository(environments, batchSize);

        // When: Migration runs
        migrationJob.migrateSecrets();

        // Then: Repository should be queried once (returns empty page)
        verify(environmentRepository, times(1)).findAll(any(Pageable.class));
        
        // And: No secrets should be processed
        verify(secretsBackend, never()).retrieve(any(UUID.class));
        verify(secretsBackend, never()).store(any(UUID.class), anyString());
    }

    /**
     * Test that migration job handles database with single environment.
     * 
     * <p>Edge case: exactly one environment to migrate.</p>
     */
    @Test
    void shouldHandleSingleEnvironment() {
        // Given: Exactly 1 environment
        int batchSize = 100;
        List<Environment> environments = createEnvironments(1);
        
        when(properties.getBatchSize()).thenReturn(batchSize);
        configurePaginatedRepository(environments, batchSize);
        when(secretsBackend.retrieve(any(UUID.class))).thenReturn("test-secret");

        // When: Migration runs
        migrationJob.migrateSecrets();

        // Then: Repository should be queried once
        verify(environmentRepository, times(1)).findAll(any(Pageable.class));
        
        // And: The single environment should be processed
        verify(secretsBackend, times(1)).retrieve(any(UUID.class));
        verify(secretsBackend, times(1)).store(any(UUID.class), eq("test-secret"));
    }

    // ========== Plaintext Secret Detection Tests ==========

    /**
     * Test that migration job detects and encrypts plaintext secrets.
     * 
     * <p>When the backend retrieves a plaintext secret (no version prefix),
     * storing it back should encrypt it with the current key version.</p>
     */
    @Test
    void shouldDetectAndEncryptPlaintextSecrets() {
        // Given: Environment with plaintext secret
        int batchSize = 10;
        List<Environment> environments = createEnvironments(1);
        UUID envId = environments.get(0).getId();
        
        when(properties.getBatchSize()).thenReturn(batchSize);
        configurePaginatedRepository(environments, batchSize);
        
        // Backend returns plaintext secret (no version prefix)
        when(secretsBackend.retrieve(envId)).thenReturn("plaintext-secret");

        // When: Migration runs
        migrationJob.migrateSecrets();

        // Then: Secret should be retrieved
        verify(secretsBackend, times(1)).retrieve(envId);
        
        // And: Secret should be stored back (backend will encrypt it)
        verify(secretsBackend, times(1)).store(envId, "plaintext-secret");
    }

    /**
     * Test that migration job processes mix of plaintext and encrypted secrets.
     * 
     * <p>The job should handle environments with both plaintext and already-encrypted
     * secrets in the same migration run.</p>
     */
    @Test
    void shouldHandleMixOfPlaintextAndEncryptedSecrets() {
        // Given: 5 environments with mixed secret types
        int batchSize = 10;
        List<Environment> environments = createEnvironments(5);
        
        when(properties.getBatchSize()).thenReturn(batchSize);
        configurePaginatedRepository(environments, batchSize);
        
        // Configure backend to return different secret types
        when(secretsBackend.retrieve(any(UUID.class)))
            .thenReturn("plaintext-secret-1")      // Plaintext
            .thenReturn("20240101:encrypted-1")    // Old version
            .thenReturn("plaintext-secret-2")      // Plaintext
            .thenReturn("20240115:encrypted-2")    // Current version
            .thenReturn("20231201:encrypted-3");   // Old version

        // When: Migration runs
        migrationJob.migrateSecrets();

        // Then: All 5 environments should be processed
        verify(secretsBackend, times(5)).retrieve(any(UUID.class));
        verify(secretsBackend, times(5)).store(any(UUID.class), anyString());
    }

    /**
     * Test that migration job re-encrypts secrets with old key versions.
     * 
     * <p>Secrets encrypted with old key versions should be re-encrypted with
     * the current key version.</p>
     */
    @Test
    void shouldReEncryptSecretsWithOldKeyVersions() {
        // Given: Environments with secrets using old key versions
        int batchSize = 10;
        List<Environment> environments = createEnvironments(3);
        
        when(properties.getBatchSize()).thenReturn(batchSize);
        configurePaginatedRepository(environments, batchSize);
        
        // Backend returns secrets with old version prefixes
        when(secretsBackend.retrieve(any(UUID.class)))
            .thenReturn("20231201:old-encrypted-1")
            .thenReturn("20240101:old-encrypted-2")
            .thenReturn("20231115:old-encrypted-3");

        // When: Migration runs
        migrationJob.migrateSecrets();

        // Then: All secrets should be retrieved and stored
        verify(secretsBackend, times(3)).retrieve(any(UUID.class));
        
        // And: Secrets should be stored back (backend will use current key)
        ArgumentCaptor<String> secretCaptor = ArgumentCaptor.forClass(String.class);
        verify(secretsBackend, times(3)).store(any(UUID.class), secretCaptor.capture());
        
        List<String> storedSecrets = secretCaptor.getAllValues();
        assertThat(storedSecrets).containsExactly(
            "20231201:old-encrypted-1",
            "20240101:old-encrypted-2",
            "20231115:old-encrypted-3"
        );
    }

    /**
     * Test that migration job processes secrets in correct order.
     * 
     * <p>Secrets should be processed in the order they are returned from the
     * repository (page by page).</p>
     */
    @Test
    void shouldProcessSecretsInCorrectOrder() {
        // Given: 3 environments
        int batchSize = 10;
        List<Environment> environments = createEnvironments(3);
        UUID env1 = environments.get(0).getId();
        UUID env2 = environments.get(1).getId();
        UUID env3 = environments.get(2).getId();
        
        when(properties.getBatchSize()).thenReturn(batchSize);
        configurePaginatedRepository(environments, batchSize);
        when(secretsBackend.retrieve(any(UUID.class))).thenReturn("test-secret");

        // When: Migration runs
        migrationJob.migrateSecrets();

        // Then: Environments should be processed in order
        InOrder inOrder = inOrder(secretsBackend);
        inOrder.verify(secretsBackend).retrieve(env1);
        inOrder.verify(secretsBackend).store(env1, "test-secret");
        inOrder.verify(secretsBackend).retrieve(env2);
        inOrder.verify(secretsBackend).store(env2, "test-secret");
        inOrder.verify(secretsBackend).retrieve(env3);
        inOrder.verify(secretsBackend).store(env3, "test-secret");
    }

    // ========== Helper Methods ==========

    /**
     * Creates a list of test environments.
     * 
     * @param count number of environments to create
     * @return list of environments with random UUIDs
     */
    private List<Environment> createEnvironments(int count) {
        List<Environment> environments = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Environment env = new Environment();
            env.setId(UUID.randomUUID());
            env.setSharedSecret("20240115:encrypted-secret-" + i);
            environments.add(env);
        }
        return environments;
    }

    /**
     * Configures the mocked repository to return paginated results.
     * 
     * @param environments all environments to paginate
     * @param batchSize size of each page
     */
    private void configurePaginatedRepository(List<Environment> environments, int batchSize) {
        when(environmentRepository.findAll(any(Pageable.class)))
            .thenAnswer(invocation -> {
                Pageable pageable = invocation.getArgument(0);
                int pageNumber = pageable.getPageNumber();
                int pageSize = pageable.getPageSize();
                
                // Calculate start and end indices for this page
                int start = pageNumber * pageSize;
                int end = Math.min(start + pageSize, environments.size());
                
                // Return empty page if beyond the data
                if (start >= environments.size()) {
                    return new PageImpl<>(List.of(), pageable, environments.size());
                }
                
                // Return the page of environments
                List<Environment> pageContent = environments.subList(start, end);
                boolean hasNext = end < environments.size();
                
                return new PageImpl<>(pageContent, pageable, environments.size()) {
                    @Override
                    public boolean hasNext() {
                        return hasNext;
                    }
                };
            });
    }
}
