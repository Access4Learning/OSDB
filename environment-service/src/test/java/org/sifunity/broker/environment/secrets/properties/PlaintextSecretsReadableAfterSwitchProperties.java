// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.properties;

import net.jqwik.api.*;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.secrets.impl.MultiKeyBackend;
import org.sifunity.broker.environment.secrets.config.MultiKeyProperties;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for backward compatibility when switching from PlaintextBackend
 * to MultiKeyBackend.
 * 
 * <p>These tests validate that when switching from PlaintextBackend to MultiKeyBackend,
 * existing plaintext secrets stored in the database can still be read without errors.
 * The MultiKeyBackend's retrieve() method handles plaintext secrets (no version prefix)
 * by detecting the absence of the "YYYYMMDD:" format and returning them as-is with a
 * warning log.</p>
 * 
 * <p>This backward compatibility is critical for zero-downtime migrations where:
 * <ol>
 *   <li>System starts with PlaintextBackend, secrets stored as plaintext</li>
 *   <li>Configuration changes to MultiKeyBackend</li>
 *   <li>Application restarts with MultiKeyBackend</li>
 *   <li>Existing plaintext secrets must still be readable</li>
 *   <li>Migration job later encrypts plaintext secrets</li>
 * </ol>
 * 
 * <p><strong>Validates: Requirements 10.1</strong></p>
 * 
 * <p><strong>Property 18: Plaintext Secrets Readable After Backend Switch</strong></p>
 * <p>For any environment with a plaintext secret stored in the database, switching to
 * the Multi-Key Backend should allow the secret to be read without errors.</p>
 */
@Tag("pluggable-secrets-backend")
class PlaintextSecretsReadableAfterSwitchProperties {

    // ========== Property 18: Plaintext Secrets Readable After Backend Switch ==========

    /**
     * Property 18.1: MultiKeyBackend reads plaintext secrets without errors
     * 
     * <p>For any plaintext secret (no version prefix), the MultiKeyBackend should
     * successfully retrieve it without throwing exceptions.</p>
     * 
     * <p>This property validates the core backward compatibility requirement: when
     * switching from PlaintextBackend to MultiKeyBackend, existing plaintext secrets
     * must remain accessible.</p>
     * 
     * <p><strong>Validates: Requirements 10.1</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-18-Plaintext-Secrets-Readable-After-Backend-Switch")
    void multiKeyBackendReadsPlaintextSecretsWithoutErrors(
            @ForAll("plaintextSecrets") String plaintextSecret) {
        
        // Given: An environment with a plaintext secret (simulating PlaintextBackend storage)
        BackendSwitchTestHelper helper = new BackendSwitchTestHelper();
        UUID environmentId = helper.createEnvironmentWithPlaintextSecret(plaintextSecret);
        
        // When: We switch to MultiKeyBackend and retrieve the secret
        String retrievedSecret = helper.retrieveWithMultiKeyBackend(environmentId);
        
        // Then: The secret should be retrieved successfully without errors
        assertThat(retrievedSecret)
            .as("MultiKeyBackend should retrieve plaintext secret without errors")
            .isNotNull();
    }

    /**
     * Property 18.2: MultiKeyBackend preserves plaintext secret values
     * 
     * <p>For any plaintext secret, the value retrieved by MultiKeyBackend should
     * exactly match the original plaintext value stored by PlaintextBackend.</p>
     * 
     * <p>This property validates that the backward compatibility mechanism doesn't
     * corrupt or modify the secret data.</p>
     * 
     * <p><strong>Validates: Requirements 10.1</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-18-Plaintext-Secrets-Readable-After-Backend-Switch")
    void multiKeyBackendPreservesPlaintextSecretValues(
            @ForAll("plaintextSecrets") String originalPlaintextSecret) {
        
        // Given: An environment with a plaintext secret
        BackendSwitchTestHelper helper = new BackendSwitchTestHelper();
        UUID environmentId = helper.createEnvironmentWithPlaintextSecret(originalPlaintextSecret);
        
        // When: We retrieve the secret with MultiKeyBackend
        String retrievedSecret = helper.retrieveWithMultiKeyBackend(environmentId);
        
        // Then: The retrieved value should match the original plaintext exactly
        assertThat(retrievedSecret)
            .as("MultiKeyBackend should preserve plaintext secret value exactly")
            .isEqualTo(originalPlaintextSecret);
    }

    /**
     * Property 18.3: MultiKeyBackend handles empty plaintext secrets
     * 
     * <p>For empty plaintext secrets, the MultiKeyBackend should retrieve them
     * correctly and return an empty string.</p>
     * 
     * <p>This property validates backward compatibility for edge cases like empty secrets.</p>
     * 
     * <p><strong>Validates: Requirements 10.1</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-18-Plaintext-Secrets-Readable-After-Backend-Switch")
    void multiKeyBackendHandlesEmptyPlaintextSecrets() {
        
        // Given: An environment with an empty plaintext secret
        String emptySecret = "";
        BackendSwitchTestHelper helper = new BackendSwitchTestHelper();
        UUID environmentId = helper.createEnvironmentWithPlaintextSecret(emptySecret);
        
        // When: We retrieve the secret with MultiKeyBackend
        String retrievedSecret = helper.retrieveWithMultiKeyBackend(environmentId);
        
        // Then: The retrieved value should be empty
        assertThat(retrievedSecret)
            .as("MultiKeyBackend should handle empty plaintext secrets")
            .isEmpty();
    }

    /**
     * Property 18.4: MultiKeyBackend handles plaintext secrets with special characters
     * 
     * <p>For plaintext secrets containing special characters (Unicode, symbols, whitespace),
     * the MultiKeyBackend should retrieve them correctly without data corruption.</p>
     * 
     * <p>This property validates that special characters in plaintext secrets are preserved
     * during the backend switch.</p>
     * 
     * <p><strong>Validates: Requirements 10.1</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-18-Plaintext-Secrets-Readable-After-Backend-Switch")
    void multiKeyBackendHandlesPlaintextSecretsWithSpecialCharacters(
            @ForAll("secretsWithSpecialChars") String plaintextSecret) {
        
        // Given: An environment with a plaintext secret containing special characters
        BackendSwitchTestHelper helper = new BackendSwitchTestHelper();
        UUID environmentId = helper.createEnvironmentWithPlaintextSecret(plaintextSecret);
        
        // When: We retrieve the secret with MultiKeyBackend
        String retrievedSecret = helper.retrieveWithMultiKeyBackend(environmentId);
        
        // Then: All special characters should be preserved
        assertThat(retrievedSecret)
            .as("MultiKeyBackend should preserve special characters in plaintext secrets")
            .isEqualTo(plaintextSecret);
    }

    /**
     * Property 18.5: MultiKeyBackend handles long plaintext secrets
     * 
     * <p>For long plaintext secrets (up to 1000 characters), the MultiKeyBackend should
     * retrieve them correctly without truncation or data loss.</p>
     * 
     * <p>This property validates that the backward compatibility mechanism works for
     * secrets of various lengths.</p>
     * 
     * <p><strong>Validates: Requirements 10.1</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-18-Plaintext-Secrets-Readable-After-Backend-Switch")
    void multiKeyBackendHandlesLongPlaintextSecrets(
            @ForAll("longSecrets") String longPlaintextSecret) {
        
        // Given: An environment with a long plaintext secret
        BackendSwitchTestHelper helper = new BackendSwitchTestHelper();
        UUID environmentId = helper.createEnvironmentWithPlaintextSecret(longPlaintextSecret);
        
        // When: We retrieve the secret with MultiKeyBackend
        String retrievedSecret = helper.retrieveWithMultiKeyBackend(environmentId);
        
        // Then: The entire secret should be preserved
        assertThat(retrievedSecret)
            .as("MultiKeyBackend should handle long plaintext secrets")
            .isEqualTo(longPlaintextSecret)
            .hasSize(longPlaintextSecret.length());
    }

    /**
     * Property 18.6: MultiKeyBackend distinguishes plaintext from encrypted secrets
     * 
     * <p>For a mix of plaintext and encrypted secrets, the MultiKeyBackend should
     * correctly identify and handle each type appropriately.</p>
     * 
     * <p>This property validates that the backend can operate in a mixed environment
     * where some secrets have been migrated (encrypted) and others are still plaintext.</p>
     * 
     * <p><strong>Validates: Requirements 10.1</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-18-Plaintext-Secrets-Readable-After-Backend-Switch")
    void multiKeyBackendDistinguishesPlaintextFromEncryptedSecrets(
            @ForAll("plaintextSecrets") String plaintextSecret,
            @ForAll("plaintextSecrets") String secretToEncrypt) {
        
        // Given: Two environments - one with plaintext, one with encrypted secret
        BackendSwitchTestHelper helper = new BackendSwitchTestHelper();
        
        UUID plaintextEnvId = helper.createEnvironmentWithPlaintextSecret(plaintextSecret);
        UUID encryptedEnvId = helper.createEnvironmentWithEncryptedSecret(secretToEncrypt);
        
        // When: We retrieve both secrets with MultiKeyBackend
        String retrievedPlaintext = helper.retrieveWithMultiKeyBackend(plaintextEnvId);
        String retrievedEncrypted = helper.retrieveWithMultiKeyBackend(encryptedEnvId);
        
        // Then: Both should be retrieved correctly
        assertThat(retrievedPlaintext)
            .as("MultiKeyBackend should retrieve plaintext secret correctly")
            .isEqualTo(plaintextSecret);
        
        assertThat(retrievedEncrypted)
            .as("MultiKeyBackend should retrieve encrypted secret correctly")
            .isEqualTo(secretToEncrypt);
    }

    /**
     * Property 18.7: MultiKeyBackend handles plaintext secrets with colons
     * 
     * <p>For plaintext secrets that contain colons but don't match the YYYYMMDD: format,
     * the MultiKeyBackend should treat them as plaintext and return them as-is.</p>
     * 
     * <p>This property validates that the backend correctly distinguishes between:
     * <ul>
     *   <li>Encrypted secrets with version prefix (YYYYMMDD:data)</li>
     *   <li>Plaintext secrets that happen to contain colons</li>
     * </ul>
     * 
     * <p><strong>Validates: Requirements 10.1</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-18-Plaintext-Secrets-Readable-After-Backend-Switch")
    void multiKeyBackendHandlesPlaintextSecretsWithColons(
            @ForAll("secretsWithColons") String plaintextSecretWithColon) {
        
        // Given: An environment with a plaintext secret containing a colon
        BackendSwitchTestHelper helper = new BackendSwitchTestHelper();
        UUID environmentId = helper.createEnvironmentWithPlaintextSecret(plaintextSecretWithColon);
        
        // When: We retrieve the secret with MultiKeyBackend
        String retrievedSecret = helper.retrieveWithMultiKeyBackend(environmentId);
        
        // Then: The secret should be returned as-is (plaintext)
        assertThat(retrievedSecret)
            .as("MultiKeyBackend should handle plaintext secrets with colons")
            .isEqualTo(plaintextSecretWithColon);
    }

    /**
     * Property 18.8: MultiKeyBackend handles multiple plaintext secrets
     * 
     * <p>For multiple environments with plaintext secrets, the MultiKeyBackend should
     * retrieve all of them correctly without interference.</p>
     * 
     * <p>This property validates that the backward compatibility mechanism works
     * consistently across multiple environments.</p>
     * 
     * <p><strong>Validates: Requirements 10.1</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-18-Plaintext-Secrets-Readable-After-Backend-Switch")
    void multiKeyBackendHandlesMultiplePlaintextSecrets(
            @ForAll("secretLists") List<String> plaintextSecrets) {
        
        Assume.that(!plaintextSecrets.isEmpty());
        
        // Given: Multiple environments with plaintext secrets
        BackendSwitchTestHelper helper = new BackendSwitchTestHelper();
        Map<UUID, String> environmentSecrets = new HashMap<>();
        
        for (String secret : plaintextSecrets) {
            UUID envId = helper.createEnvironmentWithPlaintextSecret(secret);
            environmentSecrets.put(envId, secret);
        }
        
        // When: We retrieve all secrets with MultiKeyBackend
        Map<UUID, String> retrievedSecrets = new HashMap<>();
        for (UUID envId : environmentSecrets.keySet()) {
            String retrieved = helper.retrieveWithMultiKeyBackend(envId);
            retrievedSecrets.put(envId, retrieved);
        }
        
        // Then: All secrets should match their original values
        for (UUID envId : environmentSecrets.keySet()) {
            assertThat(retrievedSecrets.get(envId))
                .as("MultiKeyBackend should retrieve plaintext secret for environment %s", envId)
                .isEqualTo(environmentSecrets.get(envId));
        }
    }

    // ========== Arbitraries (Test Data Generators) ==========

    /**
     * Provides arbitrary plaintext secret strings for testing.
     * 
     * <p>Generates strings with alphanumeric characters and common symbols,
     * with lengths between 1 and 100 characters. Excludes colons to ensure
     * these are clearly plaintext (no version prefix).</p>
     */
    @Provide
    Arbitrary<String> plaintextSecrets() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars("!@#$%^&*()_+-=[]{}|;,.<>?")
            .ofMinLength(1)
            .ofMaxLength(100)
            .filter(s -> !s.contains(":")); // Ensure no colon
    }

    /**
     * Provides arbitrary secret strings with special characters.
     * 
     * <p>Generates strings that include common special characters, whitespace,
     * and symbols to test edge cases. Excludes colons to ensure plaintext format.</p>
     */
    @Provide
    Arbitrary<String> secretsWithSpecialChars() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .whitespace()
            .withChars("!@#$%^&*()_+-=[]{}|;,.<>?/~`'\"\\")
            .ofMinLength(1)
            .ofMaxLength(100)
            .filter(s -> !s.contains(":")); // Ensure no colon
    }

    /**
     * Provides arbitrary long secret strings for testing.
     * 
     * <p>Generates strings with lengths between 100 and 1000 characters
     * to test handling of larger secrets.</p>
     */
    @Provide
    Arbitrary<String> longSecrets() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .ofMinLength(100)
            .ofMaxLength(1000)
            .filter(s -> !s.contains(":")); // Ensure no colon
    }

    /**
     * Provides arbitrary secret strings that contain colons but don't match
     * the YYYYMMDD: format.
     * 
     * <p>These secrets should be treated as plaintext by MultiKeyBackend even
     * though they contain colons.</p>
     */
    @Provide
    Arbitrary<String> secretsWithColons() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars("!@#$%^&*()_+-=[]{}|;,.<>?")
            .ofMinLength(5)
            .ofMaxLength(100)
            .map(s -> {
                // Insert a colon at a random position
                int colonPos = Math.min(3, s.length() / 2);
                return s.substring(0, colonPos) + ":" + s.substring(colonPos);
            })
            .filter(s -> {
                // Ensure it doesn't match YYYYMMDD: format
                String[] parts = s.split(":", 2);
                return parts.length != 2 || !parts[0].matches("\\d{8}");
            });
    }

    /**
     * Provides arbitrary lists of secret strings for testing multiple environments.
     * 
     * <p>Generates lists of 1-10 secrets for testing batch operations.</p>
     */
    @Provide
    Arbitrary<List<String>> secretLists() {
        return plaintextSecrets().list().ofMinSize(1).ofMaxSize(10);
    }

    // ========== Helper Class ==========

    /**
     * Helper class for testing backend switch scenarios.
     * 
     * <p>This class simulates the scenario where:
     * <ol>
     *   <li>PlaintextBackend stores secrets as plaintext in the database</li>
     *   <li>Configuration changes to MultiKeyBackend</li>
     *   <li>MultiKeyBackend must read existing plaintext secrets</li>
     * </ol>
     * 
     * <p>The helper creates a real MultiKeyBackend instance with mocked repository
     * to test the actual retrieve() logic.</p>
     */
    private static class BackendSwitchTestHelper {
        private final MultiKeyBackend multiKeyBackend;
        private final EnvironmentRepository environmentRepository;
        private final Map<UUID, Environment> environments;
        private final String currentKeyVersion = "20240115";
        
        public BackendSwitchTestHelper() {
            this.environments = new HashMap<>();
            this.environmentRepository = mock(EnvironmentRepository.class);
            
            // Configure repository mock to return environments from our map
            when(environmentRepository.findById(any(UUID.class)))
                .thenAnswer(invocation -> {
                    UUID envId = invocation.getArgument(0);
                    return Optional.ofNullable(environments.get(envId));
                });
            
            // Create MultiKeyBackend with real encryption keys
            MultiKeyProperties properties = createMultiKeyProperties();
            this.multiKeyBackend = new MultiKeyBackend(environmentRepository, properties);
        }
        
        /**
         * Creates a MultiKeyProperties configuration with valid encryption keys.
         */
        private MultiKeyProperties createMultiKeyProperties() {
            try {
                // Generate valid AES-256 keys
                KeyGenerator keyGen = KeyGenerator.getInstance("AES");
                keyGen.init(256);
                
                Map<String, String> keys = new HashMap<>();
                
                // Generate keys for multiple versions
                for (String version : Arrays.asList("20231215", "20240101", "20240115", "20240201")) {
                    SecretKey secretKey = keyGen.generateKey();
                    String base64Key = Base64.getEncoder().encodeToString(secretKey.getEncoded());
                    keys.put(version, base64Key);
                }
                
                MultiKeyProperties properties = new MultiKeyProperties();
                properties.setCurrentVersion(currentKeyVersion);
                properties.setKeys(keys);
                
                return properties;
            } catch (Exception e) {
                throw new RuntimeException("Failed to create test encryption keys", e);
            }
        }
        
        /**
         * Creates an environment with a plaintext secret (simulating PlaintextBackend storage).
         * 
         * @param plaintextSecret the plaintext secret value
         * @return the environment ID
         */
        public UUID createEnvironmentWithPlaintextSecret(String plaintextSecret) {
            UUID envId = UUID.randomUUID();
            Environment env = new Environment();
            env.setId(envId);
            // Store as plaintext (no version prefix) - simulates PlaintextBackend
            env.setSharedSecret(plaintextSecret);
            
            environments.put(envId, env);
            return envId;
        }
        
        /**
         * Creates an environment with an encrypted secret (simulating MultiKeyBackend storage).
         * 
         * @param secretToEncrypt the secret value to encrypt
         * @return the environment ID
         */
        public UUID createEnvironmentWithEncryptedSecret(String secretToEncrypt) {
            UUID envId = UUID.randomUUID();
            Environment env = new Environment();
            env.setId(envId);
            
            // Add environment to the map first so it exists when store() is called
            environments.put(envId, env);
            
            // Store the secret using MultiKeyBackend (will encrypt it)
            multiKeyBackend.store(envId, secretToEncrypt);
            
            return envId;
        }
        
        /**
         * Retrieves a secret using MultiKeyBackend.
         * 
         * <p>This simulates the scenario after switching from PlaintextBackend to
         * MultiKeyBackend, where the backend must handle existing plaintext secrets.</p>
         * 
         * @param environmentId the environment ID
         * @return the retrieved secret value
         */
        public String retrieveWithMultiKeyBackend(UUID environmentId) {
            return multiKeyBackend.retrieve(environmentId);
        }
    }
}
