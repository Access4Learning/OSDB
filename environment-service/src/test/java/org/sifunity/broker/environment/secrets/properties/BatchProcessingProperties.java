// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.properties;

import net.jqwik.api.*;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.secrets.SecretsBackend;
import org.sifunity.broker.environment.secrets.config.MigrationProperties;
import org.sifunity.broker.environment.secrets.migration.MigrationJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for migration job batch processing.
 * 
 * <p>These tests validate that the migration job correctly processes environments in
 * batches according to the configured batch size. The tests verify that the job queries
 * the database in pages and processes all environments across multiple batches.</p>
 * 
 * <p><strong>Validates: Requirements 8.6</strong></p>
 * 
 * <p><strong>Property 11: Migration Job Processes in Batches</strong></p>
 * <p>For any migration job execution, secrets should be processed in batches of the
 * configured batch size, not all at once.</p>
 */
@Tag("pluggable-secrets-backend")
class BatchProcessingProperties {

    // ========== Property 11: Migration Job Processes in Batches ==========

    /**
     * Property 11.1: Migration job queries database in pages
     * 
     * <p>For any batch size and environment count, the migration job should query
     * the database using pagination with the configured batch size.</p>
     * 
     * <p>This property validates that the job doesn't load all environments into
     * memory at once, which would cause performance issues with large datasets.</p>
     * 
     * <p><strong>Validates: Requirements 8.6</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-11-Migration-Job-Processes-in-Batches")
    void migrationJobQueriesDatabaseInPages(
            @ForAll("batchSizes") int batchSize,
            @ForAll("environmentCounts") int environmentCount) {
        
        // Given: A migration job with a configured batch size
        MigrationTestHelper helper = new MigrationTestHelper(batchSize, environmentCount);
        
        // When: The migration job runs
        helper.runMigration();
        
        // Then: The repository should be queried with the correct batch size
        // Note: Even with 0 environments, the job queries once to check if there are any
        int expectedPageCount = environmentCount == 0 ? 1 : (int) Math.ceil((double) environmentCount / batchSize);
        
        verify(helper.environmentRepository, times(expectedPageCount))
            .findAll(argThat((Pageable pageable) -> 
                pageable.getPageSize() == batchSize
            ));
    }

    /**
     * Property 11.2: Migration job processes all environments
     * 
     * <p>For any batch size and environment count, the migration job should process
     * all environments exactly once, regardless of how they are distributed across batches.</p>
     * 
     * <p><strong>Validates: Requirements 8.6</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-11-Migration-Job-Processes-in-Batches")
    void migrationJobProcessesAllEnvironments(
            @ForAll("batchSizes") int batchSize,
            @ForAll("environmentCounts") int environmentCount) {
        
        // Given: A migration job with environments to migrate
        MigrationTestHelper helper = new MigrationTestHelper(batchSize, environmentCount);
        
        // When: The migration job runs
        helper.runMigration();
        
        // Then: All environments should be processed
        verify(helper.secretsBackend, times(environmentCount)).retrieve(any(UUID.class));
        verify(helper.secretsBackend, times(environmentCount)).store(any(UUID.class), anyString());
    }

    /**
     * Property 11.3: Migration job respects batch size configuration
     * 
     * <p>For any batch size, the migration job should request pages with exactly
     * that size from the repository.</p>
     * 
     * <p><strong>Validates: Requirements 8.6</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-11-Migration-Job-Processes-in-Batches")
    void migrationJobRespectsBatchSizeConfiguration(
            @ForAll("batchSizes") int batchSize,
            @ForAll("environmentCounts") int environmentCount) {
        
        // Given: A migration job with a specific batch size
        MigrationTestHelper helper = new MigrationTestHelper(batchSize, environmentCount);
        
        // When: The migration job runs
        helper.runMigration();
        
        // Then: All repository queries should use the configured batch size
        verify(helper.environmentRepository, atLeastOnce())
            .findAll(argThat((Pageable pageable) -> 
                pageable.getPageSize() == batchSize
            ));
    }

    /**
     * Property 11.4: Migration job queries correct number of pages
     * 
     * <p>For any batch size and environment count, the migration job should query
     * exactly the number of pages needed to cover all environments.</p>
     * 
     * <p><strong>Validates: Requirements 8.6</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-11-Migration-Job-Processes-in-Batches")
    void migrationJobQueriesCorrectNumberOfPages(
            @ForAll("batchSizes") int batchSize,
            @ForAll("environmentCounts") int environmentCount) {
        
        // Given: A migration job with environments distributed across pages
        MigrationTestHelper helper = new MigrationTestHelper(batchSize, environmentCount);
        
        // When: The migration job runs
        helper.runMigration();
        
        // Then: The number of page queries should match the expected count
        // Note: Even with 0 environments, the job queries once to check if there are any
        int expectedPageCount = environmentCount == 0 ? 1 : (int) Math.ceil((double) environmentCount / batchSize);
        
        verify(helper.environmentRepository, times(expectedPageCount))
            .findAll(any(Pageable.class));
    }

    /**
     * Property 11.5: Migration job handles partial last page
     * 
     * <p>When the total environment count is not evenly divisible by the batch size,
     * the migration job should correctly process the partial last page.</p>
     * 
     * <p><strong>Validates: Requirements 8.6</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-11-Migration-Job-Processes-in-Batches")
    void migrationJobHandlesPartialLastPage(
            @ForAll("batchSizes") int batchSize,
            @ForAll("unevenEnvironmentCounts") int environmentCount) {
        
        // Given: An environment count that doesn't divide evenly by batch size
        Assume.that(environmentCount % batchSize != 0);
        
        MigrationTestHelper helper = new MigrationTestHelper(batchSize, environmentCount);
        
        // When: The migration job runs
        helper.runMigration();
        
        // Then: All environments should still be processed
        verify(helper.secretsBackend, times(environmentCount)).retrieve(any(UUID.class));
        verify(helper.secretsBackend, times(environmentCount)).store(any(UUID.class), anyString());
    }

    /**
     * Property 11.6: Migration job processes pages sequentially
     * 
     * <p>The migration job should process pages in sequential order (page 0, 1, 2, ...)
     * to ensure consistent and predictable behavior.</p>
     * 
     * <p><strong>Validates: Requirements 8.6</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-11-Migration-Job-Processes-in-Batches")
    void migrationJobProcessesPagesSequentially(
            @ForAll("batchSizes") int batchSize,
            @ForAll("environmentCounts") int environmentCount) {
        
        // Given: A migration job with multiple pages
        MigrationTestHelper helper = new MigrationTestHelper(batchSize, environmentCount);
        
        // When: The migration job runs
        helper.runMigration();
        
        // Then: Pages should be requested in sequential order
        int expectedPageCount = (int) Math.ceil((double) environmentCount / batchSize);
        
        for (int page = 0; page < expectedPageCount; page++) {
            final int expectedPage = page;
            verify(helper.environmentRepository, atLeastOnce())
                .findAll(argThat((Pageable pageable) -> 
                    pageable.getPageNumber() == expectedPage
                ));
        }
    }

    // ========== Arbitraries (Test Data Generators) ==========

    /**
     * Provides arbitrary batch sizes for testing.
     * 
     * <p>Generates batch sizes between 1 and 500, covering small batches
     * (for testing edge cases) and larger batches (for realistic scenarios).</p>
     */
    @Provide
    Arbitrary<Integer> batchSizes() {
        return Arbitraries.integers()
            .between(1, 500);
    }

    /**
     * Provides arbitrary environment counts for testing.
     * 
     * <p>Generates environment counts between 0 and 1000, covering empty
     * databases, small datasets, and larger datasets.</p>
     */
    @Provide
    Arbitrary<Integer> environmentCounts() {
        return Arbitraries.integers()
            .between(0, 1000);
    }

    /**
     * Provides arbitrary environment counts that don't divide evenly.
     * 
     * <p>Generates environment counts between 1 and 1000 to test partial
     * last page scenarios.</p>
     */
    @Provide
    Arbitrary<Integer> unevenEnvironmentCounts() {
        return Arbitraries.integers()
            .between(1, 1000);
    }

    // ========== Helper Class ==========

    /**
     * Helper class for testing migration job batch processing.
     * 
     * <p>This class creates a migration job with mocked dependencies and provides
     * methods to run the migration and verify batch processing behavior.</p>
     */
    private static class MigrationTestHelper {
        private final MigrationJob migrationJob;
        private final SecretsBackend secretsBackend;
        private final EnvironmentRepository environmentRepository;
        private final MigrationProperties properties;
        private final int batchSize;
        private final int environmentCount;
        private final List<Environment> environments;
        
        public MigrationTestHelper(int batchSize, int environmentCount) {
            this.batchSize = batchSize;
            this.environmentCount = environmentCount;
            this.environments = createEnvironments(environmentCount);
            
            // Create mocks
            this.secretsBackend = mock(SecretsBackend.class);
            this.environmentRepository = mock(EnvironmentRepository.class);
            this.properties = mock(MigrationProperties.class);
            
            // Configure mocks
            when(properties.getBatchSize()).thenReturn(batchSize);
            
            // Configure repository to return paginated results
            configurePaginatedRepository();
            
            // Configure secrets backend to return dummy secrets
            when(secretsBackend.retrieve(any(UUID.class)))
                .thenReturn("test-secret");
            
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
        
        private void configurePaginatedRepository() {
            // Calculate number of pages
            int pageCount = (int) Math.ceil((double) environmentCount / batchSize);
            
            // Configure repository to return pages
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
        
        public void runMigration() {
            migrationJob.migrateSecrets();
        }
    }
}
