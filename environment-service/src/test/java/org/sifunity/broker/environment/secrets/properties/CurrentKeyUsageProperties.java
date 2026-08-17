// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.properties;

import net.jqwik.api.*;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.secrets.impl.MultiKeyBackend;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.lang.reflect.Field;
import java.security.SecureRandom;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for current key usage in new encryptions.
 * 
 * <p>These tests validate that any new secret stored by the Multi-Key Backend
 * is always encrypted with the current key version, not with old key versions.
 * This ensures that key rotation is effective and new secrets use the most
 * recent encryption key.</p>
 * 
 * <p><strong>Validates: Requirements 2.8, 6.3</strong></p>
 * 
 * <p><strong>Property 4: Current Key Used for New Encryptions</strong></p>
 * <p>For any new secret stored by the Multi-Key Backend, the version prefix
 * should match the currently configured key version.</p>
 */
@Tag("pluggable-secrets-backend")
class CurrentKeyUsageProperties {

    // ========== Property 4: Current Key Used for New Encryptions ==========

    /**
     * Property 4.1: New secrets use current key version
     * 
     * <p>For any valid secret string stored by the MultiKeyBackend, the stored
     * value should be prefixed with the current key version, not any older
     * key version.</p>
     * 
     * <p>This property validates that the backend correctly uses the current
     * key for all new encryption operations, which is essential for effective
     * key rotation.</p>
     * 
     * <p><strong>Validates: Requirements 2.8, 6.3</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-4-Current-Key-Used-For-New-Encryptions")
    void newSecretsUseCurrentKeyVersion(
            @ForAll("secrets") String secret,
            @ForAll("multiKeyConfigurations") MultiKeyConfiguration config) throws Exception {
        
        // Given: A MultiKeyBackend with multiple key versions configured
        BackendTestHelper helper = new BackendTestHelper(config);
        UUID environmentId = UUID.randomUUID();
        
        // When: We store a new secret
        String storedValue = helper.storeAndGetStoredValue(environmentId, secret);
        
        // Then: The stored value should be prefixed with the current key version
        assertThat(storedValue)
            .as("New secret should be encrypted with current key version")
            .startsWith(config.currentVersion + ":");
        
        // And: It should NOT be prefixed with any old key version
        for (String oldVersion : config.oldVersions) {
            assertThat(storedValue)
                .as("New secret should not be encrypted with old key version " + oldVersion)
                .doesNotStartWith(oldVersion + ":");
        }
    }

    /**
     * Property 4.2: Multiple stores always use current key
     * 
     * <p>For multiple secrets stored sequentially, all should use the current
     * key version, demonstrating consistent behavior across operations.</p>
     * 
     * <p><strong>Validates: Requirements 2.8, 6.3</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-4-Current-Key-Used-For-New-Encryptions")
    void multipleStoresAlwaysUseCurrentKey(
            @ForAll("secretsList") List<String> secrets,
            @ForAll("multiKeyConfigurations") MultiKeyConfiguration config) throws Exception {
        
        // Given: A MultiKeyBackend with multiple key versions configured
        BackendTestHelper helper = new BackendTestHelper(config);
        
        // When: We store multiple secrets
        List<String> storedValues = new ArrayList<>();
        for (String secret : secrets) {
            UUID environmentId = UUID.randomUUID();
            String storedValue = helper.storeAndGetStoredValue(environmentId, secret);
            storedValues.add(storedValue);
        }
        
        // Then: All stored values should use the current key version
        for (String storedValue : storedValues) {
            assertThat(storedValue)
                .as("All new secrets should be encrypted with current key version")
                .startsWith(config.currentVersion + ":");
        }
    }

    /**
     * Property 4.3: Current key used regardless of secret content
     * 
     * <p>For secrets with various characteristics (empty, long, special characters),
     * the current key version should always be used.</p>
     * 
     * <p><strong>Validates: Requirements 2.8, 6.3</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-4-Current-Key-Used-For-New-Encryptions")
    void currentKeyUsedRegardlessOfSecretContent(
            @ForAll("diverseSecrets") String secret,
            @ForAll("multiKeyConfigurations") MultiKeyConfiguration config) throws Exception {
        
        // Given: A MultiKeyBackend with multiple key versions configured
        BackendTestHelper helper = new BackendTestHelper(config);
        UUID environmentId = UUID.randomUUID();
        
        // When: We store a secret with diverse content
        String storedValue = helper.storeAndGetStoredValue(environmentId, secret);
        
        // Then: The current key version should be used
        String versionPrefix = storedValue.split(":", 2)[0];
        assertThat(versionPrefix)
            .as("Current key should be used regardless of secret content")
            .isEqualTo(config.currentVersion);
    }

    /**
     * Property 4.4: Current key used when old keys are configured
     * 
     * <p>For a backend with multiple old key versions configured, new secrets
     * should still use the current key, not any of the old keys.</p>
     * 
     * <p>This validates that the presence of old keys doesn't interfere with
     * the selection of the current key for new encryptions.</p>
     * 
     * <p><strong>Validates: Requirements 2.8, 6.3</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-4-Current-Key-Used-For-New-Encryptions")
    void currentKeyUsedWhenOldKeysConfigured(
            @ForAll("secrets") String secret,
            @ForAll("multiKeyConfigurationsWithManyOldKeys") MultiKeyConfiguration config) throws Exception {
        
        // Given: A MultiKeyBackend with many old key versions configured
        BackendTestHelper helper = new BackendTestHelper(config);
        UUID environmentId = UUID.randomUUID();
        
        // Verify we have multiple old keys configured
        assertThat(config.oldVersions)
            .as("Test should have multiple old keys configured")
            .hasSizeGreaterThanOrEqualTo(2);
        
        // When: We store a new secret
        String storedValue = helper.storeAndGetStoredValue(environmentId, secret);
        
        // Then: The current key version should be used
        assertThat(storedValue)
            .as("Current key should be used even when many old keys are configured")
            .startsWith(config.currentVersion + ":");
    }

    /**
     * Property 4.5: Empty secrets use current key
     * 
     * <p>For empty string secrets, the current key version should still be used.</p>
     * 
     * <p><strong>Validates: Requirements 2.8, 6.3</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-4-Current-Key-Used-For-New-Encryptions")
    void emptySecretsUseCurrentKey(
            @ForAll("multiKeyConfigurations") MultiKeyConfiguration config) throws Exception {
        
        // Given: An empty secret
        String secret = "";
        BackendTestHelper helper = new BackendTestHelper(config);
        UUID environmentId = UUID.randomUUID();
        
        // When: We store the empty secret
        String storedValue = helper.storeAndGetStoredValue(environmentId, secret);
        
        // Then: The current key version should be used
        assertThat(storedValue)
            .as("Empty secret should be encrypted with current key version")
            .startsWith(config.currentVersion + ":");
    }

    /**
     * Property 4.6: Long secrets use current key
     * 
     * <p>For long secrets (up to 1000 characters), the current key version
     * should be used.</p>
     * 
     * <p><strong>Validates: Requirements 2.8, 6.3</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-4-Current-Key-Used-For-New-Encryptions")
    void longSecretsUseCurrentKey(
            @ForAll("longSecrets") String secret,
            @ForAll("multiKeyConfigurations") MultiKeyConfiguration config) throws Exception {
        
        // Given: A long secret
        BackendTestHelper helper = new BackendTestHelper(config);
        UUID environmentId = UUID.randomUUID();
        
        // When: We store the long secret
        String storedValue = helper.storeAndGetStoredValue(environmentId, secret);
        
        // Then: The current key version should be used
        assertThat(storedValue)
            .as("Long secret should be encrypted with current key version")
            .startsWith(config.currentVersion + ":");
    }

    /**
     * Property 4.7: Current key version extracted correctly
     * 
     * <p>For any secret stored, extracting the version prefix should yield
     * exactly the current key version string.</p>
     * 
     * <p><strong>Validates: Requirements 2.8, 6.3</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-4-Current-Key-Used-For-New-Encryptions")
    void currentKeyVersionExtractedCorrectly(
            @ForAll("secrets") String secret,
            @ForAll("multiKeyConfigurations") MultiKeyConfiguration config) throws Exception {
        
        // Given: A MultiKeyBackend with multiple key versions configured
        BackendTestHelper helper = new BackendTestHelper(config);
        UUID environmentId = UUID.randomUUID();
        
        // When: We store a secret and extract the version prefix
        String storedValue = helper.storeAndGetStoredValue(environmentId, secret);
        String[] parts = storedValue.split(":", 2);
        String extractedVersion = parts[0];
        
        // Then: The extracted version should exactly match the current version
        assertThat(extractedVersion)
            .as("Extracted version should exactly match current key version")
            .isEqualTo(config.currentVersion);
    }

    // ========== Arbitraries (Test Data Generators) ==========

    /**
     * Provides arbitrary secret strings for testing.
     * 
     * <p>Generates strings with alphanumeric characters and common symbols,
     * with lengths between 1 and 100 characters.</p>
     */
    @Provide
    Arbitrary<String> secrets() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars("!@#$%^&*()_+-=[]{}|;:,.<>?")
            .ofMinLength(1)
            .ofMaxLength(100);
    }

    /**
     * Provides arbitrary lists of secret strings for testing.
     * 
     * <p>Generates lists of 2-5 secrets for testing multiple store operations.</p>
     */
    @Provide
    Arbitrary<List<String>> secretsList() {
        return secrets().list().ofMinSize(2).ofMaxSize(5);
    }

    /**
     * Provides arbitrary diverse secret strings for testing.
     * 
     * <p>Generates strings that include empty strings, special characters,
     * whitespace, and various lengths to test edge cases.</p>
     */
    @Provide
    Arbitrary<String> diverseSecrets() {
        return Arbitraries.oneOf(
            Arbitraries.just(""),  // Empty string
            Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(10),  // Short
            Arbitraries.strings().alpha().numeric().ofMinLength(100).ofMaxLength(200),  // Long
            Arbitraries.strings().whitespace().ofMinLength(1).ofMaxLength(10),  // Whitespace
            Arbitraries.strings().withChars("!@#$%^&*()").ofMinLength(1).ofMaxLength(10)  // Special chars
        );
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
            .ofMaxLength(1000);
    }

    /**
     * Provides arbitrary multi-key configurations for testing.
     * 
     * <p>Generates configurations with a current key version and 1-2 old
     * key versions to simulate key rotation scenarios.</p>
     */
    @Provide
    Arbitrary<MultiKeyConfiguration> multiKeyConfigurations() {
        return Arbitraries.of(
            new MultiKeyConfiguration("20240201", Arrays.asList("20240101")),
            new MultiKeyConfiguration("20240301", Arrays.asList("20240201", "20240101")),
            new MultiKeyConfiguration("20240115", Arrays.asList("20231215")),
            new MultiKeyConfiguration("20240401", Arrays.asList("20240301")),
            new MultiKeyConfiguration("20240601", Arrays.asList("20240501", "20240401"))
        );
    }

    /**
     * Provides arbitrary multi-key configurations with many old keys.
     * 
     * <p>Generates configurations with a current key version and 3-4 old
     * key versions to test scenarios with extensive key rotation history.</p>
     */
    @Provide
    Arbitrary<MultiKeyConfiguration> multiKeyConfigurationsWithManyOldKeys() {
        return Arbitraries.of(
            new MultiKeyConfiguration("20240401", Arrays.asList("20240301", "20240201", "20240101")),
            new MultiKeyConfiguration("20240501", Arrays.asList("20240401", "20240301", "20240201", "20240101")),
            new MultiKeyConfiguration("20240601", Arrays.asList("20240501", "20240401", "20240301"))
        );
    }

    // ========== Helper Classes ==========

    /**
     * Configuration for multi-key testing scenarios.
     * 
     * <p>Represents a backend configuration with a current key version
     * and a list of old key versions.</p>
     */
    private static class MultiKeyConfiguration {
        final String currentVersion;
        final List<String> oldVersions;
        
        MultiKeyConfiguration(String currentVersion, List<String> oldVersions) {
            this.currentVersion = currentVersion;
            this.oldVersions = oldVersions;
        }
    }

    /**
     * Helper class for testing the store method with multiple key versions.
     * 
     * <p>This class creates a MultiKeyBackend instance with a mock repository
     * and multiple configured key versions, then provides methods to store
     * secrets and retrieve the stored values.</p>
     */
    private static class BackendTestHelper {
        private final MultiKeyBackend backend;
        private final EnvironmentRepository mockRepository;
        private final MultiKeyConfiguration config;
        
        public BackendTestHelper(MultiKeyConfiguration config) throws Exception {
            this.config = config;
            
            // Generate valid AES-256 keys for all versions
            Map<String, SecretKey> keyVersionsMap = new HashMap<>();
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            
            // Add current key
            keyVersionsMap.put(config.currentVersion, keyGen.generateKey());
            
            // Add old keys
            for (String oldVersion : config.oldVersions) {
                keyVersionsMap.put(oldVersion, keyGen.generateKey());
            }
            
            // Create mock repository
            this.mockRepository = mock(EnvironmentRepository.class);
            
            // Create backend instance using reflection to bypass constructor
            this.backend = createBackendInstance(keyVersionsMap, config.currentVersion);
        }
        
        private MultiKeyBackend createBackendInstance(
                Map<String, SecretKey> keyVersionsMap,
                String currentKeyVersion) throws Exception {
            // Use sun.misc.Unsafe to create an instance without calling the constructor
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Object unsafe = unsafeField.get(null);
            java.lang.reflect.Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
            MultiKeyBackend instance = (MultiKeyBackend) allocateInstance.invoke(unsafe, MultiKeyBackend.class);
            
            // Set the required fields using reflection
            setField(instance, "keyVersions", keyVersionsMap);
            setField(instance, "currentKeyVersion", currentKeyVersion);
            setField(instance, "secureRandom", new SecureRandom());
            setField(instance, "environmentRepository", mockRepository);
            
            return instance;
        }
        
        private void setField(Object instance, String fieldName, Object value) throws Exception {
            Field field = MultiKeyBackend.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(instance, value);
        }
        
        /**
         * Stores a secret and returns the value that was stored in the database.
         */
        public String storeAndGetStoredValue(UUID environmentId, String secret) {
            // Create a mock environment
            Environment environment = new Environment();
            environment.setId(environmentId);
            
            // Configure mock to return the environment
            when(mockRepository.findById(environmentId)).thenReturn(Optional.of(environment));
            when(mockRepository.save(any(Environment.class))).thenReturn(environment);
            
            // Store the secret
            backend.store(environmentId, secret);
            
            // Return the stored value
            return environment.getSharedSecret();
        }
    }
}
