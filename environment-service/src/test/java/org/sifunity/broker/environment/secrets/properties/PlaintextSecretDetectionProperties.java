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
import static org.mockito.Mockito.*;

/**
 * Property-based tests for migration job plaintext secret detection.
 * 
 * <p>These tests validate that the migration job detects and encrypts plaintext
 * secrets when migrating from PlaintextBackend to MultiKeyBackend. The MultiKeyBackend
 * retrieve() method handles plaintext secrets (no colon in the value) by logging a
 * warning and returning them as-is. When the migration job calls store(), these
 * plaintext secrets get encrypted with the current key.</p>
 * 
 * <p><strong>Validates: Requirements 10.3</strong></p>
 * 
 * <p><strong>Property 19: Migration Job Encrypts Plaintext Secrets</strong></p>
 * <p>For any secret stored in plaintext format (no version prefix), the Migration Job
 * should detect it and re-encrypt it with the current key version.</p>
 */
@Tag("pluggable-secrets-backend")
class PlaintextSecretDetectionProperties {

    // ========== Property 19: Migration Job Encrypts Plaintext Secrets ==========

    /**
     * Property 19.1: Migration job retrieves plaintext secrets
     * 
     * <p>For any environment with a plaintext secret (no version prefix), the
     * migration job should successfully retrieve the secret value.</p>
     * 
     * <p>This property validates that the retrieve operation works correctly
     * for plaintext secrets during backward compatibility scenarios.</p>
     * 
     * <p><strong>Validates: Requirements 10.3</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-19-Migration-Job-Encrypts-Plaintext-Secrets")
    void migrationJobRetrievesPlaintextSecrets(
            @ForAll("plaintextEnvironmentCounts") int environmentCount) {
        
        Assume.that(environmentCount > 0);
        
        // Given: A migration job with environments having plaintext secrets
        PlaintextDetectionTestHelper helper = new PlaintextDetectionTestHelper(environmentCount, true);
        
        // When: The migration job runs
        helper.runMigration();
        
        // Then: Retrieve should be called for all plaintext secrets
        verify(helper.secretsBackend, times(environmentCount)).retrieve(any(UUID.class));
    }

    /**
     * Property 19.2: Migration job stores plaintext secrets after retrieval
     * 
     * <p>For any environment with a plaintext secret, the migration job should
     * call store() to encrypt the secret with the current key version.</p>
     * 
     * <p>This property validates that plaintext secrets are re-encrypted during
     * migration, improving security.</p>
     * 
     * <p><strong>Validates: Requirements 10.3</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-19-Migration-Job-Encrypts-Plaintext-Secrets")
    void migrationJobStoresPlaintextSecretsAfterRetrieval(
            @ForAll("plaintextEnvironmentCounts") int environmentCount) {
        
        Assume.that(environmentCount > 0);
        
        // Given: A migration job with environments having plaintext secrets
        PlaintextDetectionTestHelper helper = new PlaintextDetectionTestHelper(environmentCount, true);
        
        // When: The migration job runs
        helper.runMigration();
        
        // Then: Store should be called for all plaintext secrets
        verify(helper.secretsBackend, times(environmentCount)).store(any(UUID.class), anyString());
    }

    /**
     * Property 19.3: Migration job preserves plaintext secret values
     * 
     * <p>For any plaintext secret, the value passed to store() should match
     * the original plaintext value. This ensures the secret data is preserved
     * during encryption.</p>
     * 
     * <p><strong>Validates: Requirements 10.3</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-19-Migration-Job-Encrypts-Plaintext-Secrets")
    void migrationJobPreservesPlaintextSecretValues(
            @ForAll("plaintextEnvironmentCounts") int environmentCount) {
        
        Assume.that(environmentCount > 0);
        
        // Given: A migration job with environments having plaintext secrets
        PlaintextDetectionTestHelper helper = new PlaintextDetectionTestHelper(environmentCount, true);
        
        // When: The migration job runs
        helper.runMigration();
        
        // Then: Each stored secret should match the original plaintext value
        for (Environment env : helper.getEnvironments()) {
            String originalPlaintext = helper.getOriginalPlaintextSecret(env.getId());
            verify(helper.secretsBackend).retrieve(env.getId());
            verify(helper.secretsBackend).store(env.getId(), originalPlaintext);
        }
    }

    /**
     * Property 19.4: Migration job handles mixed plaintext and encrypted secrets
     * 
     * <p>For any set of environments with a mix of plaintext and encrypted secrets,
     * the migration job should successfully process all of them.</p>
     * 
     * <p>This property validates the backward compatibility scenario where some
     * secrets are already encrypted and others are still in plaintext.</p>
     * 
     * <p><strong>Validates: Requirements 10.3</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-19-Migration-Job-Encrypts-Plaintext-Secrets")
    void migrationJobHandlesMixedPlaintextAndEncryptedSecrets(
            @ForAll("plaintextEnvironmentCounts") int plaintextCount,
            @ForAll("encryptedEnvironmentCounts") int encryptedCount) {
        
        int totalCount = plaintextCount + encryptedCount;
        Assume.that(totalCount > 0);
        
        // Given: A migration job with mixed plaintext and encrypted secrets
        PlaintextDetectionTestHelper helper = new PlaintextDetectionTestHelper(
            plaintextCount, encryptedCount);
        
        // When: The migration job runs
        helper.runMigration();
        
        // Then: All secrets should be processed
        verify(helper.secretsBackend, times(totalCount)).retrieve(any(UUID.class));
        verify(helper.secretsBackend, times(totalCount)).store(any(UUID.class), anyString());
    }

    /**
     * Property 19.5: Migration job detects plaintext secrets without version prefix
     * 
     * <p>For any plaintext secret (no colon separator), the migration job should
     * detect it as plaintext and process it correctly.</p>
     * 
     * <p>This property validates that the job correctly identifies plaintext secrets
     * by the absence of the version prefix format.</p>
     * 
     * <p><strong>Validates: Requirements 10.3</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-19-Migration-Job-Encrypts-Plaintext-Secrets")
    void migrationJobDetectsPlaintextSecretsWithoutVersionPrefix(
            @ForAll("plaintextEnvironmentCounts") int environmentCount) {
        
        Assume.that(environmentCount > 0);
        
        // Given: A migration job with plaintext secrets (no colon)
        PlaintextDetectionTestHelper helper = new PlaintextDetectionTestHelper(environmentCount, true);
        
        // Verify all test secrets are plaintext (no colon)
        for (Environment env : helper.getEnvironments()) {
            assertThat(env.getSharedSecret()).doesNotContain(":");
        }
        
        // When: The migration job runs
        helper.runMigration();
        
        // Then: All plaintext secrets should be processed
        verify(helper.secretsBackend, times(environmentCount)).retrieve(any(UUID.class));
        verify(helper.secretsBackend, times(environmentCount)).store(any(UUID.class), anyString());
    }

    /**
     * Property 19.6: Migration job handles plaintext secrets with special characters
     * 
     * <p>For any plaintext secret containing special characters, the migration job
     * should correctly retrieve and store the secret without data corruption.</p>
     * 
     * <p>This property validates that special characters in plaintext secrets are
     * preserved during migration.</p>
     * 
     * <p><strong>Validates: Requirements 10.3</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-19-Migration-Job-Encrypts-Plaintext-Secrets")
    void migrationJobHandlesPlaintextSecretsWithSpecialCharacters(
            @ForAll("specialCharacterSecrets") String plaintextSecret) {
        
        // Given: A migration job with a plaintext secret containing special characters
        PlaintextDetectionTestHelper helper = new PlaintextDetectionTestHelper(
            List.of(plaintextSecret));
        
        // When: The migration job runs
        helper.runMigration();
        
        // Then: The secret should be retrieved and stored with the same value
        verify(helper.secretsBackend).retrieve(any(UUID.class));
        verify(helper.secretsBackend).store(any(UUID.class), eq(plaintextSecret));
    }

    // ========== Arbitraries (Test Data Generators) ==========

    /**
     * Provides arbitrary counts of plaintext environments for testing.
     * 
     * <p>Generates counts between 0 and 100 for faster test execution.</p>
     */
    @Provide
    Arbitrary<Integer> plaintextEnvironmentCounts() {
        return Arbitraries.integers()
            .between(0, 100);
    }

    /**
     * Provides arbitrary counts of encrypted environments for testing.
     * 
     * <p>Generates counts between 0 and 100 for faster test execution.</p>
     */
    @Provide
    Arbitrary<Integer> encryptedEnvironmentCounts() {
        return Arbitraries.integers()
            .between(0, 100);
    }

    /**
     * Provides arbitrary plaintext secrets with special characters.
     * 
     * <p>Generates secrets with various special characters to test edge cases.</p>
     */
    @Provide
    Arbitrary<String> specialCharacterSecrets() {
        return Arbitraries.strings()
            .withCharRange('!', '~')
            .ofMinLength(10)
            .ofMaxLength(100)
            .filter(s -> !s.contains(":")); // Ensure no colon (plaintext format)
    }

    // ========== Helper Class ==========

    /**
     * Helper class for testing migration job plaintext secret detection.
     * 
     * <p>This class creates a migration job with mocked dependencies and simulates
     * the behavior of MultiKeyBackend when handling plaintext secrets.</p>
     */
    private static class PlaintextDetectionTestHelper {
        private final MigrationJob migrationJob;
        private final SecretsBackend secretsBackend;
        private final EnvironmentRepository environmentRepository;
        private final MigrationProperties properties;
        private final List<Environment> environments;
        private final Map<UUID, String> plaintextSecretsByEnvironment;
        
        /**
         * Constructor for creating environments with only plaintext secrets.
         * 
         * @param plaintextCount number of plaintext environments to create
         * @param onlyPlaintext if true, all environments have plaintext secrets
         */
        public PlaintextDetectionTestHelper(int plaintextCount, boolean onlyPlaintext) {
            this(plaintextCount, 0);
        }
        
        /**
         * Constructor for creating environments with mixed plaintext and encrypted secrets.
         * 
         * @param plaintextCount number of plaintext environments
         * @param encryptedCount number of encrypted environments
         */
        public PlaintextDetectionTestHelper(int plaintextCount, int encryptedCount) {
            this.environments = createMixedEnvironments(plaintextCount, encryptedCount);
            this.plaintextSecretsByEnvironment = new HashMap<>();
            
            // Create mocks
            this.secretsBackend = mock(SecretsBackend.class);
            this.environmentRepository = mock(EnvironmentRepository.class);
            this.properties = mock(MigrationProperties.class);
            
            // Configure mocks
            when(properties.getBatchSize()).thenReturn(50);
            
            // Configure repository to return paginated results
            configurePaginatedRepository();
            
            // Configure secrets backend to simulate MultiKeyBackend behavior
            configureSecretsBackend();
            
            // Create migration job
            this.migrationJob = new MigrationJob(secretsBackend, environmentRepository, properties);
        }
        
        /**
         * Constructor for creating environments with specific plaintext secrets.
         * 
         * @param plaintextSecrets list of plaintext secret values
         */
        public PlaintextDetectionTestHelper(List<String> plaintextSecrets) {
            this.environments = createEnvironmentsWithSecrets(plaintextSecrets);
            this.plaintextSecretsByEnvironment = new HashMap<>();
            
            // Create mocks
            this.secretsBackend = mock(SecretsBackend.class);
            this.environmentRepository = mock(EnvironmentRepository.class);
            this.properties = mock(MigrationProperties.class);
            
            // Configure mocks
            when(properties.getBatchSize()).thenReturn(50);
            
            // Configure repository to return paginated results
            configurePaginatedRepository();
            
            // Configure secrets backend to simulate MultiKeyBackend behavior
            configureSecretsBackend();
            
            // Create migration job
            this.migrationJob = new MigrationJob(secretsBackend, environmentRepository, properties);
        }
        
        private List<Environment> createMixedEnvironments(int plaintextCount, int encryptedCount) {
            List<Environment> envs = new ArrayList<>();
            
            // Create plaintext environments
            for (int i = 0; i < plaintextCount; i++) {
                Environment env = new Environment();
                env.setId(UUID.randomUUID());
                // Plaintext secret (no version prefix, no colon)
                env.setSharedSecret("plaintext-secret-" + i);
                envs.add(env);
            }
            
            // Create encrypted environments
            for (int i = 0; i < encryptedCount; i++) {
                Environment env = new Environment();
                env.setId(UUID.randomUUID());
                // Encrypted secret with version prefix
                env.setSharedSecret("20240115:encrypted-secret-" + i);
                envs.add(env);
            }
            
            return envs;
        }
        
        private List<Environment> createEnvironmentsWithSecrets(List<String> plaintextSecrets) {
            List<Environment> envs = new ArrayList<>();
            
            for (String secret : plaintextSecrets) {
                Environment env = new Environment();
                env.setId(UUID.randomUUID());
                env.setSharedSecret(secret);
                envs.add(env);
            }
            
            return envs;
        }
        
        private void configurePaginatedRepository() {
            int batchSize = 50;
            int environmentCount = environments.size();
            
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
            // Configure retrieve to simulate MultiKeyBackend behavior
            when(secretsBackend.retrieve(any(UUID.class)))
                .thenAnswer(invocation -> {
                    UUID envId = invocation.getArgument(0);
                    
                    // Find the environment
                    Environment env = environments.stream()
                        .filter(e -> e.getId().equals(envId))
                        .findFirst()
                        .orElseThrow();
                    
                    String storedValue = env.getSharedSecret();
                    
                    // Simulate MultiKeyBackend.retrieve() behavior:
                    // If no colon, return as plaintext
                    if (!storedValue.contains(":")) {
                        plaintextSecretsByEnvironment.put(envId, storedValue);
                        return storedValue;
                    }
                    
                    // If has version prefix (YYYYMMDD:), return "decrypted" value
                    String[] parts = storedValue.split(":", 2);
                    String potentialVersion = parts[0];
                    
                    if (potentialVersion.matches("\\d{8}")) {
                        // Encrypted secret - return "decrypted" value
                        String decryptedSecret = "decrypted-" + parts[1];
                        return decryptedSecret;
                    }
                    
                    // Otherwise, treat as plaintext with colon
                    plaintextSecretsByEnvironment.put(envId, storedValue);
                    return storedValue;
                });
            
            // Configure store (no-op for testing)
            doNothing().when(secretsBackend).store(any(UUID.class), anyString());
        }
        
        public void runMigration() {
            migrationJob.migrateSecrets();
        }
        
        public List<Environment> getEnvironments() {
            return environments;
        }
        
        public String getOriginalPlaintextSecret(UUID envId) {
            return plaintextSecretsByEnvironment.get(envId);
        }
    }
}
