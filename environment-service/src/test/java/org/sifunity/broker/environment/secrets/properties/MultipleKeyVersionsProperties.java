// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.properties;

import net.jqwik.api.*;
import org.sifunity.broker.environment.secrets.impl.MultiKeyBackend;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.SecureRandom;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for multiple key versions support.
 * 
 * <p>These tests validate that the MultiKeyBackend can be configured with
 * multiple key versions and can decrypt secrets encrypted with any of those
 * versions. This is essential for key rotation support, where multiple keys
 * must be active simultaneously to maintain access to secrets encrypted at
 * different points in time.</p>
 * 
 * <p><strong>Validates: Requirements 6.2</strong></p>
 * 
 * <p><strong>Property 9: Multiple Key Versions Supported</strong></p>
 * <p>For any Multi-Key Backend configuration with multiple key versions, all
 * configured key versions should be available for decryption operations.</p>
 */
@Tag("pluggable-secrets-backend")
class MultipleKeyVersionsProperties {

    // ========== Property 9: Multiple Key Versions Supported ==========

    /**
     * Property 9.1: Backend accepts configuration with multiple key versions
     * 
     * <p>For any valid configuration with multiple key versions, the MultiKeyBackend
     * should initialize successfully and maintain all configured key versions in its
     * internal mapping. This validates that the backend can handle multiple keys
     * simultaneously.</p>
     * 
     * <p><strong>Validates: Requirements 6.2</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-9-Multiple-Key-Versions-Supported")
    void backendAcceptsMultipleKeyVersions(
            @ForAll("multiKeyConfigurations") MultiKeyConfiguration config) throws Exception {
        
        // Given: A configuration with multiple key versions
        MultiKeyTestHelper helper = new MultiKeyTestHelper(config);
        
        // When: We check the configured key versions
        Map<String, SecretKey> keyVersions = helper.getKeyVersions();
        
        // Then: All configured key versions should be present
        assertThat(keyVersions)
            .as("Backend should maintain all configured key versions")
            .hasSize(config.getAllVersions().size())
            .containsKeys(config.getAllVersions().toArray(new String[0]));
    }

    /**
     * Property 9.2: Backend decrypts with any configured key version
     * 
     * <p>For any secret encrypted with any configured key version, the backend
     * should successfully decrypt it. This validates that all key versions are
     * not only stored but also functional for decryption operations.</p>
     * 
     * <p><strong>Validates: Requirements 6.2</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-9-Multiple-Key-Versions-Supported")
    void backendDecryptsWithAnyConfiguredKeyVersion(
            @ForAll("secrets") String originalSecret,
            @ForAll("multiKeyConfigurations") MultiKeyConfiguration config) throws Exception {
        
        // Given: A backend with multiple key versions configured
        MultiKeyTestHelper helper = new MultiKeyTestHelper(config);
        
        // When: We encrypt and decrypt with each configured key version
        for (String keyVersion : config.getAllVersions()) {
            String encrypted = helper.encrypt(originalSecret, keyVersion);
            String decrypted = helper.decrypt(encrypted, keyVersion);
            
            // Then: Each key version should successfully decrypt its encrypted secrets
            assertThat(decrypted)
                .as("Backend should decrypt secret encrypted with key version %s", keyVersion)
                .isEqualTo(originalSecret);
        }
    }

    /**
     * Property 9.3: Backend maintains distinct keys for each version
     * 
     * <p>For any secret encrypted with different key versions, the encrypted
     * outputs should be different (with high probability), demonstrating that
     * each key version uses a distinct encryption key.</p>
     * 
     * <p><strong>Validates: Requirements 6.2</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-9-Multiple-Key-Versions-Supported")
    void backendMaintainsDistinctKeysForEachVersion(
            @ForAll("secrets") String originalSecret,
            @ForAll("multiKeyConfigurationsWithMultipleKeys") MultiKeyConfiguration config) throws Exception {
        
        // Given: A backend with multiple key versions configured
        Assume.that(config.getAllVersions().size() >= 2);
        MultiKeyTestHelper helper = new MultiKeyTestHelper(config);
        
        // When: We encrypt the same secret with different key versions
        List<String> keyVersions = config.getAllVersions();
        String encrypted1 = helper.encrypt(originalSecret, keyVersions.get(0));
        String encrypted2 = helper.encrypt(originalSecret, keyVersions.get(1));
        
        // Then: The encrypted outputs should be different (different keys produce different ciphertext)
        // Note: The encrypt method returns just the encrypted data without version prefix
        assertThat(encrypted1)
            .as("Different key versions should produce different encrypted outputs")
            .isNotEqualTo(encrypted2);
    }

    /**
     * Property 9.4: Backend handles mixed key versions in workload
     * 
     * <p>For a workload containing secrets encrypted with different key versions,
     * the backend should correctly decrypt all of them. This simulates a realistic
     * scenario during key rotation where some secrets use old keys and some use
     * new keys.</p>
     * 
     * <p><strong>Validates: Requirements 6.2</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-9-Multiple-Key-Versions-Supported")
    void backendHandlesMixedKeyVersionsInWorkload(
            @ForAll("secretsList") List<String> secrets,
            @ForAll("multiKeyConfigurationsWithMultipleKeys") MultiKeyConfiguration config) throws Exception {
        
        // Given: A backend with multiple key versions configured
        Assume.that(config.getAllVersions().size() >= 2);
        Assume.that(secrets.size() >= 2);
        MultiKeyTestHelper helper = new MultiKeyTestHelper(config);
        
        // When: We encrypt different secrets with different key versions
        List<String> keyVersions = config.getAllVersions();
        Map<String, String> encryptedSecrets = new HashMap<>();
        Map<String, String> keyVersionUsed = new HashMap<>();
        
        for (int i = 0; i < secrets.size(); i++) {
            String secret = secrets.get(i);
            String keyVersion = keyVersions.get(i % keyVersions.size());
            String encrypted = helper.encrypt(secret, keyVersion);
            
            encryptedSecrets.put(secret, encrypted);
            keyVersionUsed.put(secret, keyVersion);
        }
        
        // Then: All secrets should decrypt correctly with their respective key versions
        for (String originalSecret : secrets) {
            String encrypted = encryptedSecrets.get(originalSecret);
            String keyVersion = keyVersionUsed.get(originalSecret);
            String decrypted = helper.decrypt(encrypted, keyVersion);
            
            assertThat(decrypted)
                .as("Secret encrypted with version %s should decrypt correctly", keyVersion)
                .isEqualTo(originalSecret);
        }
    }

    /**
     * Property 9.5: Backend supports adding new key versions
     * 
     * <p>For a backend initially configured with N key versions, adding a new
     * key version should not affect the ability to decrypt secrets encrypted
     * with the original N key versions. This validates that the key version
     * mapping is extensible.</p>
     * 
     * <p><strong>Validates: Requirements 6.2</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-9-Multiple-Key-Versions-Supported")
    void backendSupportsAddingNewKeyVersions(
            @ForAll("secrets") String originalSecret,
            @ForAll("multiKeyConfigurations") MultiKeyConfiguration config) throws Exception {
        
        // Given: A backend with initial key versions
        MultiKeyTestHelper helper = new MultiKeyTestHelper(config);
        String oldKeyVersion = config.getAllVersions().get(0);
        
        // When: We encrypt a secret with an old key version
        String encrypted = helper.encrypt(originalSecret, oldKeyVersion);
        
        // And: We add a new key version to the configuration
        String newKeyVersion = "20240601";
        helper.addKeyVersion(newKeyVersion);
        
        // Then: The secret encrypted with the old key should still decrypt correctly
        String decrypted = helper.decrypt(encrypted, oldKeyVersion);
        
        assertThat(decrypted)
            .as("Adding new key version should not affect decryption of secrets encrypted with old keys")
            .isEqualTo(originalSecret);
        
        // And: The new key version should be available for encryption
        String encryptedWithNew = helper.encrypt(originalSecret, newKeyVersion);
        String decryptedWithNew = helper.decrypt(encryptedWithNew, newKeyVersion);
        
        assertThat(decryptedWithNew)
            .as("New key version should be functional for encryption and decryption")
            .isEqualTo(originalSecret);
    }

    /**
     * Property 9.6: Backend supports many key versions
     * 
     * <p>For a backend configured with many key versions (simulating extensive
     * key rotation history), all key versions should remain functional for
     * decryption. This validates that the backend can scale to support long-term
     * key rotation practices.</p>
     * 
     * <p><strong>Validates: Requirements 6.2</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-9-Multiple-Key-Versions-Supported")
    void backendSupportsManyKeyVersions(
            @ForAll("secrets") String originalSecret,
            @ForAll("multiKeyConfigurationsWithManyKeys") MultiKeyConfiguration config) throws Exception {
        
        // Given: A backend with many key versions configured
        Assume.that(config.getAllVersions().size() >= 5);
        MultiKeyTestHelper helper = new MultiKeyTestHelper(config);
        
        // When: We encrypt and decrypt with each key version
        for (String keyVersion : config.getAllVersions()) {
            String encrypted = helper.encrypt(originalSecret, keyVersion);
            String decrypted = helper.decrypt(encrypted, keyVersion);
            
            // Then: All key versions should work correctly
            assertThat(decrypted)
                .as("Backend should support many key versions (tested with %s)", keyVersion)
                .isEqualTo(originalSecret);
        }
        
        // And: The backend should maintain all key versions
        assertThat(helper.getKeyVersions())
            .as("Backend should maintain all configured key versions")
            .hasSize(config.getAllVersions().size());
    }

    /**
     * Property 9.7: Backend correctly uses key version for decryption
     * 
     * <p>For secrets encrypted with different key versions, the backend should
     * correctly decrypt them using the corresponding key. This validates that
     * the key version mapping works correctly with multiple keys.</p>
     * 
     * <p><strong>Validates: Requirements 6.2</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-9-Multiple-Key-Versions-Supported")
    void backendCorrectlyUsesKeyVersionForDecryption(
            @ForAll("secrets") String originalSecret,
            @ForAll("multiKeyConfigurationsWithMultipleKeys") MultiKeyConfiguration config) throws Exception {
        
        // Given: A backend with multiple key versions configured
        Assume.that(config.getAllVersions().size() >= 2);
        MultiKeyTestHelper helper = new MultiKeyTestHelper(config);
        
        // When: We encrypt and decrypt secrets with different key versions
        for (String keyVersion : config.getAllVersions()) {
            String encrypted = helper.encrypt(originalSecret, keyVersion);
            
            // Then: Decryption should use the correct key and return the original secret
            String decrypted = helper.decrypt(encrypted, keyVersion);
            assertThat(decrypted)
                .as("Backend should use correct key version %s for decryption", keyVersion)
                .isEqualTo(originalSecret);
        }
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
     * <p>Generates lists of 2-5 secrets for testing multiple operations.</p>
     */
    @Provide
    Arbitrary<List<String>> secretsList() {
        return secrets().list().ofMinSize(2).ofMaxSize(5);
    }

    /**
     * Provides arbitrary multi-key configurations for testing.
     * 
     * <p>Generates configurations with a current key version and 1-3 additional
     * key versions to simulate key rotation scenarios.</p>
     */
    @Provide
    Arbitrary<MultiKeyConfiguration> multiKeyConfigurations() {
        return Arbitraries.of(
            new MultiKeyConfiguration("20240201", Arrays.asList("20240101")),
            new MultiKeyConfiguration("20240301", Arrays.asList("20240201", "20240101")),
            new MultiKeyConfiguration("20240401", Arrays.asList("20240301", "20240201", "20240101")),
            new MultiKeyConfiguration("20240115", Arrays.asList("20231215")),
            new MultiKeyConfiguration("20240501", Arrays.asList("20240401", "20240301"))
        );
    }

    /**
     * Provides arbitrary multi-key configurations with multiple keys.
     * 
     * <p>Generates configurations guaranteed to have at least 2 key versions
     * for tests that require multiple keys.</p>
     */
    @Provide
    Arbitrary<MultiKeyConfiguration> multiKeyConfigurationsWithMultipleKeys() {
        return Arbitraries.of(
            new MultiKeyConfiguration("20240201", Arrays.asList("20240101")),
            new MultiKeyConfiguration("20240301", Arrays.asList("20240201", "20240101")),
            new MultiKeyConfiguration("20240401", Arrays.asList("20240301", "20240201", "20240101")),
            new MultiKeyConfiguration("20240501", Arrays.asList("20240401", "20240301", "20240201"))
        );
    }

    /**
     * Provides arbitrary multi-key configurations with many keys.
     * 
     * <p>Generates configurations with 5+ key versions to test scenarios
     * with extensive key rotation history.</p>
     */
    @Provide
    Arbitrary<MultiKeyConfiguration> multiKeyConfigurationsWithManyKeys() {
        return Arbitraries.of(
            new MultiKeyConfiguration("20240601", Arrays.asList("20240501", "20240401", "20240301", "20240201", "20240101")),
            new MultiKeyConfiguration("20240701", Arrays.asList("20240601", "20240501", "20240401", "20240301", "20240201", "20240101")),
            new MultiKeyConfiguration("20240801", Arrays.asList("20240701", "20240601", "20240501", "20240401", "20240301"))
        );
    }

    // ========== Helper Classes ==========

    /**
     * Configuration for multi-key testing scenarios.
     * 
     * <p>Represents a backend configuration with a current key version
     * and a list of additional key versions.</p>
     */
    private static class MultiKeyConfiguration {
        final String currentVersion;
        final List<String> additionalVersions;
        
        MultiKeyConfiguration(String currentVersion, List<String> additionalVersions) {
            this.currentVersion = currentVersion;
            this.additionalVersions = additionalVersions;
        }
        
        /**
         * Returns all key versions (current + additional).
         */
        List<String> getAllVersions() {
            List<String> all = new ArrayList<>();
            all.add(currentVersion);
            all.addAll(additionalVersions);
            return all;
        }
    }

    /**
     * Helper class for testing multi-key encryption/decryption.
     * 
     * <p>This class creates a MultiKeyBackend instance with multiple configured
     * key versions and provides access to the private encrypt/decrypt methods
     * for testing purposes.</p>
     */
    private static class MultiKeyTestHelper {
        private final Object backendInstance;
        private final Method encryptMethod;
        private final Method decryptMethod;
        private final Map<String, SecretKey> keyVersionsMap;
        
        public MultiKeyTestHelper(MultiKeyConfiguration config) throws Exception {
            // Generate valid AES-256 keys for all configured key versions
            this.keyVersionsMap = generateKeyVersions(config.getAllVersions());
            
            // Create a MultiKeyBackend instance with all key versions configured
            this.backendInstance = createBackendInstance(keyVersionsMap, config.currentVersion);
            
            // Get references to the private encrypt/decrypt methods
            this.encryptMethod = MultiKeyBackend.class.getDeclaredMethod("encrypt", String.class, String.class);
            this.encryptMethod.setAccessible(true);
            
            this.decryptMethod = MultiKeyBackend.class.getDeclaredMethod("decrypt", String.class, String.class);
            this.decryptMethod.setAccessible(true);
        }
        
        /**
         * Generates encryption keys for the specified key versions.
         */
        private Map<String, SecretKey> generateKeyVersions(List<String> versions) throws Exception {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            
            Map<String, SecretKey> keys = new HashMap<>();
            for (String version : versions) {
                keys.put(version, keyGen.generateKey());
            }
            
            return keys;
        }
        
        private Object createBackendInstance(Map<String, SecretKey> keyVersions, String currentKeyVersion) throws Exception {
            // Use sun.misc.Unsafe to create an instance without calling the constructor
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Object unsafe = unsafeField.get(null);
            Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
            Object instance = allocateInstance.invoke(unsafe, MultiKeyBackend.class);
            
            // Set the required fields using reflection
            setField(instance, "keyVersions", keyVersions);
            setField(instance, "currentKeyVersion", currentKeyVersion);
            setField(instance, "secureRandom", new SecureRandom());
            setField(instance, "environmentRepository", null);
            
            return instance;
        }
        
        private void setField(Object instance, String fieldName, Object value) throws Exception {
            Field field = MultiKeyBackend.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(instance, value);
        }
        
        public String encrypt(String plaintext, String version) throws Exception {
            return (String) encryptMethod.invoke(backendInstance, plaintext, version);
        }
        
        public String decrypt(String encrypted, String version) throws Exception {
            return (String) decryptMethod.invoke(backendInstance, encrypted, version);
        }
        
        /**
         * Returns the key versions map from the backend instance.
         */
        @SuppressWarnings("unchecked")
        public Map<String, SecretKey> getKeyVersions() throws Exception {
            Field field = MultiKeyBackend.class.getDeclaredField("keyVersions");
            field.setAccessible(true);
            return (Map<String, SecretKey>) field.get(backendInstance);
        }
        
        /**
         * Adds a new key version to the backend configuration.
         * 
         * <p>This simulates adding a new key version during key rotation.</p>
         */
        public void addKeyVersion(String newVersion) throws Exception {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            SecretKey newKey = keyGen.generateKey();
            
            keyVersionsMap.put(newVersion, newKey);
            setField(backendInstance, "keyVersions", keyVersionsMap);
        }
    }
}
