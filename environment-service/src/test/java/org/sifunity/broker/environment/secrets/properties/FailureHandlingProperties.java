// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.properties;

import net.jqwik.api.*;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.secrets.SecretsBackend;
import org.sifunity.broker.environment.secrets.SecretsBackendException;
import org.sifunity.broker.environment.secrets.config.MigrationProperties;
import org.sifunity.broker.environment.secrets.migration.MigrationJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for migration job failure handling.
 * 
 * <p>These tests validate that the migration job continues processing all environments
 * even when individual secret migrations fail. The job should log failures but not stop
 * the entire migration process.</p>
 * 
 * <p><strong>Validates: Requirements 8.9</strong></p>
 * 
 * <p><strong>Property 12: Migration Job Continues on Individual Failures</strong></p>
 * <p>For any migration job execution where individual secret migrations fail, the job
 * should continue processing remaining secrets rather than stopping.</p>
 */
@Tag("pluggable-secrets-backend")
class FailureHandlingProperties {

    // ========== Property 12: Migration Job Continues on Individual Failures ==========

    /**
     * Property 12.1: Migration job processes all environments despite failures
     * 
     * <p>For any set of environments where some fail to migrate, the migration job
     * should attempt to process all environments, not stopping at the first failure.</p>
     * 
     * <p>This property validates that the job is resilient to individual failures and
     * ensures maximum migration coverage even when some secrets cannot be migrated.</p>
     * 
     * <p><strong>Validates: Requirements 8.9</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-12-Migration-Job-Continues-on-Individual-Failures")
    void migrationJobProcessesAllEnvironmentsDespiteFailures(
            @ForAll("environmentCounts") int environmentCount,
            @ForAll("failureRatios") double failureRatio) {
        
        // Given: A migration job with some environments that will fail
        FailureTestHelper helper = new FailureTestHelper(environmentCount, failureRatio);
        
        // When: The migration job runs
        helper.runMigration();
        
        // Then: All environments should be attempted (retrieve called for each)
        verify(helper.secretsBackend, times(environmentCount)).retrieve(any(UUID.class));
    }

    /**
     * Property 12.2: Migration job counts successful and failed migrations correctly
     * 
     * <p>For any migration execution with failures, the job should correctly track
     * the number of successful migrations and the number of failures.</p>
     * 
     * <p><strong>Validates: Requirements 8.9</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-12-Migration-Job-Continues-on-Individual-Failures")
    void migrationJobCountsSuccessAndFailureCorrectly(
            @ForAll("environmentCounts") int environmentCount,
            @ForAll("failureRatios") double failureRatio) {
        
        // Given: A migration job with some environments that will fail
        FailureTestHelper helper = new FailureTestHelper(environmentCount, failureRatio);
        
        // When: The migration job runs
        helper.runMigration();
        
        // Then: The number of store calls should equal successful migrations
        int expectedSuccessful = environmentCount - helper.getFailingEnvironmentCount();
        verify(helper.secretsBackend, times(expectedSuccessful)).store(any(UUID.class), anyString());
    }

    /**
     * Property 12.3: Migration job processes environments after failures
     * 
     * <p>When an environment fails to migrate, the job should continue processing
     * subsequent environments in the same batch and in following batches.</p>
     * 
     * <p><strong>Validates: Requirements 8.9</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-12-Migration-Job-Continues-on-Individual-Failures")
    void migrationJobProcessesEnvironmentsAfterFailures(
            @ForAll("environmentCounts") int environmentCount,
            @ForAll("failureRatios") double failureRatio) {
        
        Assume.that(environmentCount > 1);
        Assume.that(failureRatio > 0 && failureRatio < 1.0);
        
        // Given: A migration job with failures in the middle of the dataset
        FailureTestHelper helper = new FailureTestHelper(environmentCount, failureRatio);
        
        // Ensure at least one environment will succeed (due to rounding issues)
        int expectedSuccessful = environmentCount - helper.getFailingEnvironmentCount();
        Assume.that(expectedSuccessful > 0);
        
        // When: The migration job runs
        helper.runMigration();
        
        // Then: Environments after failures should still be processed
        // Verify that retrieve was called for all environments
        verify(helper.secretsBackend, times(environmentCount)).retrieve(any(UUID.class));
        
        // And: Some successful stores should have occurred after failures
        assertThat(expectedSuccessful).isGreaterThan(0);
        verify(helper.secretsBackend, times(expectedSuccessful)).store(any(UUID.class), anyString());
    }

    /**
     * Property 12.4: Migration job handles all environments failing
     * 
     * <p>In the extreme case where all environments fail to migrate, the job should
     * attempt all of them and complete without throwing an exception.</p>
     * 
     * <p><strong>Validates: Requirements 8.9</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-12-Migration-Job-Continues-on-Individual-Failures")
    void migrationJobHandlesAllEnvironmentsFailing(
            @ForAll("environmentCounts") int environmentCount) {
        
        Assume.that(environmentCount > 0);
        
        // Given: A migration job where all environments will fail
        FailureTestHelper helper = new FailureTestHelper(environmentCount, 1.0);
        
        // When: The migration job runs (should not throw exception)
        helper.runMigration();
        
        // Then: All environments should have been attempted
        verify(helper.secretsBackend, times(environmentCount)).retrieve(any(UUID.class));
        
        // And: No stores should have succeeded
        verify(helper.secretsBackend, never()).store(any(UUID.class), anyString());
    }

    /**
     * Property 12.5: Migration job handles no environments failing
     * 
     * <p>When no environments fail, the job should successfully migrate all of them.</p>
     * 
     * <p><strong>Validates: Requirements 8.9</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-12-Migration-Job-Continues-on-Individual-Failures")
    void migrationJobHandlesNoEnvironmentsFailing(
            @ForAll("environmentCounts") int environmentCount) {
        
        // Given: A migration job where no environments will fail
        FailureTestHelper helper = new FailureTestHelper(environmentCount, 0.0);
        
        // When: The migration job runs
        helper.runMigration();
        
        // Then: All environments should be successfully migrated
        verify(helper.secretsBackend, times(environmentCount)).retrieve(any(UUID.class));
        verify(helper.secretsBackend, times(environmentCount)).store(any(UUID.class), anyString());
    }

    /**
     * Property 12.6: Migration job handles failures across multiple batches
     * 
     * <p>When failures occur in different batches, the job should continue processing
     * all batches and not stop after a batch with failures.</p>
     * 
     * <p><strong>Validates: Requirements 8.9</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-12-Migration-Job-Continues-on-Individual-Failures")
    void migrationJobHandlesFailuresAcrossMultipleBatches(
            @ForAll("batchSizes") int batchSize,
            @ForAll("largeEnvironmentCounts") int environmentCount,
            @ForAll("failureRatios") double failureRatio) {
        
        // Ensure we have multiple batches
        Assume.that(environmentCount > batchSize);
        
        // Given: A migration job with failures distributed across batches
        FailureTestHelper helper = new FailureTestHelper(batchSize, environmentCount, failureRatio);
        
        // When: The migration job runs
        helper.runMigration();
        
        // Then: All batches should be processed
        int expectedPageCount = (int) Math.ceil((double) environmentCount / batchSize);
        verify(helper.environmentRepository, times(expectedPageCount)).findAll(any(Pageable.class));
        
        // And: All environments should be attempted
        verify(helper.secretsBackend, times(environmentCount)).retrieve(any(UUID.class));
    }

    // ========== Arbitraries (Test Data Generators) ==========

    /**
     * Provides arbitrary environment counts for testing.
     * 
     * <p>Generates environment counts between 0 and 100 for faster test execution.</p>
     */
    @Provide
    Arbitrary<Integer> environmentCounts() {
        return Arbitraries.integers()
            .between(0, 100);
    }

    /**
     * Provides arbitrary large environment counts for multi-batch testing.
     * 
     * <p>Generates environment counts between 50 and 300 to ensure multiple batches.</p>
     */
    @Provide
    Arbitrary<Integer> largeEnvironmentCounts() {
        return Arbitraries.integers()
            .between(50, 300);
    }

    /**
     * Provides arbitrary batch sizes for testing.
     * 
     * <p>Generates batch sizes between 10 and 100.</p>
     */
    @Provide
    Arbitrary<Integer> batchSizes() {
        return Arbitraries.integers()
            .between(10, 100);
    }

    /**
     * Provides arbitrary failure ratios for testing.
     * 
     * <p>Generates failure ratios between 0.0 (no failures) and 1.0 (all failures).</p>
     */
    @Provide
    Arbitrary<Double> failureRatios() {
        return Arbitraries.doubles()
            .between(0.0, 1.0);
    }

    // ========== Helper Class ==========

    /**
     * Helper class for testing migration job failure handling.
     * 
     * <p>This class creates a migration job with mocked dependencies and configures
     * some environments to fail during migration.</p>
     */
    private static class FailureTestHelper {
        private final MigrationJob migrationJob;
        private final SecretsBackend secretsBackend;
        private final EnvironmentRepository environmentRepository;
        private final MigrationProperties properties;
        private final int batchSize;
        private final int environmentCount;
        private final List<Environment> environments;
        private final Set<UUID> failingEnvironmentIds;
        
        /**
         * Constructor with default batch size of 50.
         */
        public FailureTestHelper(int environmentCount, double failureRatio) {
            this(50, environmentCount, failureRatio);
        }
        
        /**
         * Constructor with custom batch size.
         */
        public FailureTestHelper(int batchSize, int environmentCount, double failureRatio) {
            this.batchSize = batchSize;
            this.environmentCount = environmentCount;
            this.environments = createEnvironments(environmentCount);
            this.failingEnvironmentIds = selectFailingEnvironments(failureRatio);
            
            // Create mocks
            this.secretsBackend = mock(SecretsBackend.class);
            this.environmentRepository = mock(EnvironmentRepository.class);
            this.properties = mock(MigrationProperties.class);
            
            // Configure mocks
            when(properties.getBatchSize()).thenReturn(batchSize);
            
            // Configure repository to return paginated results
            configurePaginatedRepository();
            
            // Configure secrets backend with failures
            configureSecretsBackendWithFailures();
            
            // Create migration job
            this.migrationJob = new MigrationJob(secretsBackend, environmentRepository, properties);
        }
        
        private List<Environment> createEnvironments(int count) {
            List<Environment> envs = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Environment env = new Environment();
                env.setId(UUID.randomUUID());
                env.setSharedSecret("20240115:encrypted-secret-" + i);
                envs.add(env);
            }
            return envs;
        }
        
        private Set<UUID> selectFailingEnvironments(double failureRatio) {
            Set<UUID> failing = new HashSet<>();
            int failureCount = (int) Math.round(environmentCount * failureRatio);
            
            // Handle edge case: if failureRatio is 1.0, all should fail
            if (failureRatio >= 1.0) {
                for (Environment env : environments) {
                    failing.add(env.getId());
                }
                return failing;
            }
            
            // Handle edge case: if failureRatio is 0.0, none should fail
            if (failureRatio <= 0.0 || failureCount == 0) {
                return failing;
            }
            
            // Select environments to fail (evenly distributed)
            for (int i = 0; i < failureCount && i < environments.size(); i++) {
                int index = (int) ((double) i / failureCount * environments.size());
                if (index < environments.size()) {
                    failing.add(environments.get(index).getId());
                }
            }
            
            return failing;
        }
        
        private void configurePaginatedRepository() {
            when(environmentRepository.findAll(any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(0);
                    int pageNumber = pageable.getPageNumber();
                    int pageSize = pageable.getPageSize();
                    
                    // Calculate start and end indices for this page
                    int start = pageNumber * pageSize;
                    int end = Math.min(start + pageSize, environmentCount);
                    
                    // Return empty page if beyond the data
                    if (start >= environmentCount) {
                        return new PageImpl<>(List.of(), pageable, environmentCount);
                    }
                    
                    // Return the page of environments
                    List<Environment> pageContent = environments.subList(start, end);
                    boolean hasNext = end < environmentCount;
                    
                    return new PageImpl<>(pageContent, pageable, environmentCount) {
                        @Override
                        public boolean hasNext() {
                            return hasNext;
                        }
                    };
                });
        }
        
        private void configureSecretsBackendWithFailures() {
            // Configure retrieve to throw exception for failing environments
            when(secretsBackend.retrieve(any(UUID.class)))
                .thenAnswer(invocation -> {
                    UUID envId = invocation.getArgument(0);
                    if (failingEnvironmentIds.contains(envId)) {
                        throw new SecretsBackendException("Simulated failure for environment " + envId);
                    }
                    return "test-secret-" + envId;
                });
            
            // Configure store to succeed for non-failing environments
            doNothing().when(secretsBackend).store(any(UUID.class), anyString());
        }
        
        public void runMigration() {
            migrationJob.migrateSecrets();
        }
        
        public int getFailingEnvironmentCount() {
            return failingEnvironmentIds.size();
        }
    }
}
