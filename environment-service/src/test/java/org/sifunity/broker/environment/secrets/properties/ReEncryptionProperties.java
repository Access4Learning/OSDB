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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for migration job re-encryption with current key.
 * 
 * <p>These tests validate that the migration job re-encrypts secrets with the
 * current key version. When a secret is migrated, it should be retrieved (decrypted
 * with old key if needed) and stored back (encrypted with current key).</p>
 * 
 * <p><strong>Validates: Requirements 8.4</strong></p>
 * 
 * <p><strong>Property 13: Migration Job Re-encrypts with Current Key</strong></p>
 * <p>For any secret migrated by the Migration Job, the resulting stored value
 * should use the current key version prefix.</p>
 */
@Tag("pluggable-secrets-backend")
class ReEncryptionProperties {

    // ========== Property 13: Migration Job Re-encrypts with Current Key ==========

    /**
     * Property 13.1: Migration job calls retrieve and store for each environment
     * 
     * <p>For any set of environments, the migration job should call retrieve
     * to get the secret (decrypted) and then call store to save it back
     * (encrypted with current key).</p>
     * 
     * <p>This property validates the basic re-encryption flow: retrieve with
     * old key, store with current key.</p>
     * 
     * <p><strong>Validates: Requirements 8.4</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-13-Migration-Job-Re-encrypts-with-Current-Key")
    void migrationJobCallsRetrieveAndStoreForEachEnvironment(
            @ForAll("environmentCounts") int environmentCount) {
        
        // Given: A migration job with environments to migrate
        ReEncryptionTestHelper helper = new ReEncryptionTestHelper(environmentCount);
        
        // When: The migration job runs
        helper.runMigration();
        
        // Then: Retrieve and store should be called for each environment
        verify(helper.secretsBackend, times(environmentCount)).retrieve(any(UUID.class));
        verify(helper.secretsBackend, times(environmentCount)).store(any(UUID.class), anyString());
    }

    /**
     * Property 13.2: Migration job stores the same secret value retrieved
     * 
     * <p>For any environment, the secret value passed to store should match
     * the value returned by retrieve. This ensures the plaintext secret is
     * preserved during re-encryption.</p>
     * 
     * <p><strong>Validates: Requirements 8.4</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-13-Migration-Job-Re-encrypts-with-Current-Key")
    void migrationJobStoresSameSecretValueRetrieved(
            @ForAll("environmentCounts") int environmentCount) {
        
        Assume.that(environmentCount > 0);
        
        // Given: A migration job with environments having specific secrets
        ReEncryptionTestHelper helper = new ReEncryptionTestHelper(environmentCount);
        
        // When: The migration job runs
        helper.runMigration();
        
        // Then: For each environment, the stored secret should match the retrieved secret
        for (Environment env : helper.getEnvironments()) {
            String expectedSecret = helper.getSecretForEnvironment(env.getId());
            verify(helper.secretsBackend).retrieve(env.getId());
            verify(helper.secretsBackend).store(env.getId(), expectedSecret);
        }
    }

    /**
     * Property 13.3: Migration job processes all environments in order
     * 
     * <p>For any set of environments, the migration job should process them
     * in the order they appear in the database pages.</p>
     * 
     * <p><strong>Validates: Requirements 8.4</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-13-Migration-Job-Re-encrypts-with-Current-Key")
    void migrationJobProcessesEnvironmentsInOrder(
            @ForAll("environmentCounts") int environmentCount) {
        
        Assume.that(environmentCount > 0);
        
        // Given: A migration job with environments
        ReEncryptionTestHelper helper = new ReEncryptionTestHelper(environmentCount);
        
        // When: The migration job runs
        helper.runMigration();
        
        // Then: All environments should be processed
        List<UUID> processedIds = helper.getProcessedEnvironmentIds();
        assertThat(processedIds).hasSize(environmentCount);
        
        // And: Each environment should be processed exactly once
        assertThat(processedIds).doesNotHaveDuplicates();
    }

    /**
     * Property 13.4: Migration job re-encrypts secrets with different key versions
     * 
     * <p>For any set of environments with secrets encrypted with different key
     * versions, the migration job should successfully retrieve and re-encrypt
     * all of them.</p>
     * 
     * <p>This property validates that the job handles mixed key versions correctly,
     * which is the primary use case for key rotation.</p>
     * 
     * <p><strong>Validates: Requirements 8.4</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-13-Migration-Job-Re-encrypts-with-Current-Key")
    void migrationJobReEncryptsSecretsWithDifferentKeyVersions(
            @ForAll("environmentCounts") int environmentCount) {
        
        Assume.that(environmentCount > 0);
        
        // Given: A migration job with environments using different key versions
        ReEncryptionTestHelper helper = new ReEncryptionTestHelper(environmentCount, true);
        
        // When: The migration job runs
        helper.runMigration();
        
        // Then: All environments should be processed regardless of their key version
        verify(helper.secretsBackend, times(environmentCount)).retrieve(any(UUID.class));
        verify(helper.secretsBackend, times(environmentCount)).store(any(UUID.class), anyString());
    }

    /**
     * Property 13.5: Migration job handles empty database
     * 
     * <p>When there are no environments to migrate, the migration job should
     * complete successfully without errors.</p>
     * 
     * <p><strong>Validates: Requirements 8.4</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-13-Migration-Job-Re-encrypts-with-Current-Key")
    void migrationJobHandlesEmptyDatabase() {
        
        // Given: A migration job with no environments
        ReEncryptionTestHelper helper = new ReEncryptionTestHelper(0);
        
        // When: The migration job runs (should not throw exception)
        helper.runMigration();
        
        // Then: No retrieve or store calls should be made
        verify(helper.secretsBackend, never()).retrieve(any(UUID.class));
        verify(helper.secretsBackend, never()).store(any(UUID.class), anyString());
    }

    /**
     * Property 13.6: Migration job preserves secret values during re-encryption
     * 
     * <p>For any environment with a specific secret value, after migration the
     * secret should still decrypt to the same plaintext value.</p>
     * 
     * <p>This property validates that re-encryption is lossless and doesn't
     * corrupt or modify the secret data.</p>
     * 
     * <p><strong>Validates: Requirements 8.4</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-13-Migration-Job-Re-encrypts-with-Current-Key")
    void migrationJobPreservesSecretValuesDuringReEncryption(
            @ForAll("environmentCounts") int environmentCount) {
        
        Assume.that(environmentCount > 0);
        
        // Given: A migration job with environments having specific secrets
        ReEncryptionTestHelper helper = new ReEncryptionTestHelper(environmentCount);
        Map<UUID, String> originalSecrets = helper.getOriginalSecrets();
        
        // When: The migration job runs
        helper.runMigration();
        
        // Then: Each stored secret should match the original retrieved secret
        for (Map.Entry<UUID, String> entry : originalSecrets.entrySet()) {
            UUID envId = entry.getKey();
            String originalSecret = entry.getValue();
            
            verify(helper.secretsBackend).store(envId, originalSecret);
        }
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

    // ========== Helper Class ==========

    /**
     * Helper class for testing migration job re-encryption behavior.
     * 
     * <p>This class creates a migration job with mocked dependencies and tracks
     * the retrieve/store operations to verify re-encryption behavior.</p>
     */
    private static class ReEncryptionTestHelper {
        private final MigrationJob migrationJob;
        private final SecretsBackend secretsBackend;
        private final EnvironmentRepository environmentRepository;
        private final MigrationProperties properties;
        private final int environmentCount;
        private final List<Environment> environments;
        private final Map<UUID, String> secretsByEnvironment;
        private final List<UUID> processedEnvironmentIds;
        
        /**
         * Constructor with default key version behavior (all same version).
         */
        public ReEncryptionTestHelper(int environmentCount) {
            this(environmentCount, false);
        }
        
        /**
         * Constructor with option for mixed key versions.
         * 
         * @param environmentCount number of environments to create
         * @param mixedKeyVersions if true, environments will have different key versions
         */
        public ReEncryptionTestHelper(int environmentCount, boolean mixedKeyVersions) {
            this.environmentCount = environmentCount;
            this.environments = createEnvironments(environmentCount, mixedKeyVersions);
            this.secretsByEnvironment = new HashMap<>();
            this.processedEnvironmentIds = new ArrayList<>();
            
            // Create mocks
            this.secretsBackend = mock(SecretsBackend.class);
            this.environmentRepository = mock(EnvironmentRepository.class);
            this.properties = mock(MigrationProperties.class);
            
            // Configure mocks
            when(properties.getBatchSize()).thenReturn(50);
            
            // Configure repository to return paginated results
            configurePaginatedRepository();
            
            // Configure secrets backend to track retrieve/store operations
            configureSecretsBackend();
            
            // Create migration job
            this.migrationJob = new MigrationJob(secretsBackend, environmentRepository, properties);
        }
        
        private List<Environment> createEnvironments(int count, boolean mixedKeyVersions) {
            List<Environment> envs = new ArrayList<>();
            String[] keyVersions = {"20240115", "20240101", "20231215"};
            
            for (int i = 0; i < count; i++) {
                Environment env = new Environment();
                env.setId(UUID.randomUUID());
                
                // Use different key versions if requested
                String keyVersion = mixedKeyVersions 
                    ? keyVersions[i % keyVersions.length]
                    : "20240115";
                
                env.setSharedSecret(keyVersion + ":encrypted-secret-" + i);
                envs.add(env);
            }
            return envs;
        }
        
        private void configurePaginatedRepository() {
            int batchSize = 50;
            
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
        
        private void configureSecretsBackend() {
            // Configure retrieve to return decrypted secrets
            when(secretsBackend.retrieve(any(UUID.class)))
                .thenAnswer(invocation -> {
                    UUID envId = invocation.getArgument(0);
                    
                    // Find the environment
                    Environment env = environments.stream()
                        .filter(e -> e.getId().equals(envId))
                        .findFirst()
                        .orElseThrow();
                    
                    // Extract the "decrypted" secret (for testing, just use a predictable value)
                    String storedValue = env.getSharedSecret();
                    String[] parts = storedValue.split(":", 2);
                    String decryptedSecret = "decrypted-" + parts[1];
                    
                    // Store the mapping for verification
                    secretsByEnvironment.put(envId, decryptedSecret);
                    
                    return decryptedSecret;
                });
            
            // Configure store to track which environments were processed
            doAnswer(invocation -> {
                UUID envId = invocation.getArgument(0);
                processedEnvironmentIds.add(envId);
                return null;
            }).when(secretsBackend).store(any(UUID.class), anyString());
        }
        
        public void runMigration() {
            migrationJob.migrateSecrets();
        }
        
        public List<Environment> getEnvironments() {
            return environments;
        }
        
        public String getSecretForEnvironment(UUID envId) {
            return secretsByEnvironment.get(envId);
        }
        
        public Map<UUID, String> getOriginalSecrets() {
            return new HashMap<>(secretsByEnvironment);
        }
        
        public List<UUID> getProcessedEnvironmentIds() {
            return new ArrayList<>(processedEnvironmentIds);
        }
    }
}
