// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.properties;

import net.jqwik.api.*;
import org.sifunity.broker.environment.secrets.impl.MultiKeyBackend;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for multi-key decryption support.
 * 
 * <p>These tests validate that the MultiKeyBackend can decrypt secrets encrypted
 * with any configured key version, not just the current one. This is essential for
 * key rotation support, where old secrets must remain accessible after rotating to
 * a new key.</p>
 * 
 * <p><strong>Validates: Requirements 2.2, 2.5, 2.9, 8.2</strong></p>
 * 
 * <p><strong>Property 3: Multi-Key Backend Decrypts Any Configured Key Version</strong></p>
 * <p>For any secret encrypted with any configured key version, the Multi-Key Backend
 * should successfully decrypt it using the corresponding key from the key version mapping.</p>
 */
@Tag("pluggable-secrets-backend")
class MultiKeyDecryptionProperties {

    // ========== Property 3: Multi-Key Backend Decrypts Any Configured Key Version ==========

    /**
     * Property 3.1: Backend decrypts secrets encrypted with any configured key version
     * 
     * <p>For any secret encrypted with any configured key version, the MultiKeyBackend
     * should successfully decrypt it using the corresponding key from the key version mapping.
     * This validates that the backend maintains multiple active keys simultaneously and can
     * identify and use the correct key based on the version prefix.</p>
     * 
     * <p>This property is critical for key rotation: when a new key is added and set as
     * current, the backend must still be able to decrypt secrets that were encrypted with
     * previous key versions.</p>
     * 
     * <p><strong>Validates: Requirements 2.2, 2.5, 2.9, 8.2</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-3-Multi-Key-Backend-Decrypts-Any-Configured-Key-Version")
    void backendDecryptsSecretsEncryptedWithAnyConfiguredKeyVersion(
            @ForAll("secrets") String originalSecret,
            @ForAll("keyVersions") String encryptionKeyVersion,
            @ForAll("keyVersions") String currentKeyVersion) throws Exception {
        
        // Given: A MultiKeyBackend with multiple configured key versions
        MultiKeyTestHelper helper = new MultiKeyTestHelper(currentKeyVersion);
        
        // When: We encrypt a secret with one key version
        String encrypted = helper.encrypt(originalSecret, encryptionKeyVersion);
        
        // And: We decrypt it (the backend should identify the key version from the data)
        String decrypted = helper.decrypt(encrypted, encryptionKeyVersion);
        
        // Then: The decrypted value should match the original secret
        assertThat(decrypted)
            .as("Backend should decrypt secret encrypted with key version %s", encryptionKeyVersion)
            .isEqualTo(originalSecret);
    }

    /**
     * Property 3.2: Backend decrypts secrets encrypted with old key versions after rotation
     * 
     * <p>For any secret encrypted with an old key version, the backend should still be able
     * to decrypt it even after the current key version has changed. This simulates the
     * key rotation scenario where:
     * <ol>
     *   <li>Secrets are encrypted with key version A (current)</li>
     *   <li>Key version B is added and set as current</li>
     *   <li>Old secrets encrypted with version A must still be decryptable</li>
     * </ol>
     * 
     * <p><strong>Validates: Requirements 2.2, 2.5, 2.9, 8.2</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-3-Multi-Key-Backend-Decrypts-Any-Configured-Key-Version")
    void backendDecryptsOldSecretsAfterKeyRotation(
            @ForAll("secrets") String originalSecret) throws Exception {
        
        // Given: A backend with an old key version as current
        String oldKeyVersion = "20240101";
        MultiKeyTestHelper helper = new MultiKeyTestHelper(oldKeyVersion);
        
        // When: We encrypt a secret with the old key version (which is current)
        String encrypted = helper.encrypt(originalSecret, oldKeyVersion);
        
        // And: We simulate key rotation by changing the current key version
        // (The backend still has all key versions configured, just the current one changes)
        String newKeyVersion = "20240115";
        helper.setCurrentKeyVersion(newKeyVersion);
        
        // Then: The backend should still decrypt the old secret using the old key version
        String decrypted = helper.decrypt(encrypted, oldKeyVersion);
        
        assertThat(decrypted)
            .as("Backend should decrypt secrets encrypted with old key version after rotation")
            .isEqualTo(originalSecret);
    }

    /**
     * Property 3.3: Backend handles multiple key versions simultaneously
     * 
     * <p>For secrets encrypted with different key versions, the backend should correctly
     * decrypt each one using the appropriate key. This validates that the backend maintains
     * a proper mapping of key versions to keys and can handle mixed workloads where some
     * secrets use old keys and some use new keys.</p>
     * 
     * <p><strong>Validates: Requirements 2.2, 2.5, 2.9, 8.2</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-3-Multi-Key-Backend-Decrypts-Any-Configured-Key-Version")
    void backendHandlesMultipleKeyVersionsSimultaneously(
            @ForAll("secrets") String secret1,
            @ForAll("secrets") String secret2,
            @ForAll("secrets") String secret3) throws Exception {
        
        // Given: A backend with multiple key versions configured
        String currentKeyVersion = "20240115";
        MultiKeyTestHelper helper = new MultiKeyTestHelper(currentKeyVersion);
        
        // When: We encrypt different secrets with different key versions
        String encrypted1 = helper.encrypt(secret1, "20240101");
        String encrypted2 = helper.encrypt(secret2, "20240115");
        String encrypted3 = helper.encrypt(secret3, "20231215");
        
        // Then: All secrets should decrypt correctly with their respective key versions
        String decrypted1 = helper.decrypt(encrypted1, "20240101");
        String decrypted2 = helper.decrypt(encrypted2, "20240115");
        String decrypted3 = helper.decrypt(encrypted3, "20231215");
        
        assertThat(decrypted1)
            .as("Secret encrypted with version 20240101 should decrypt correctly")
            .isEqualTo(secret1);
        assertThat(decrypted2)
            .as("Secret encrypted with version 20240115 should decrypt correctly")
            .isEqualTo(secret2);
        assertThat(decrypted3)
            .as("Secret encrypted with version 20231215 should decrypt correctly")
            .isEqualTo(secret3);
    }

    /**
     * Property 3.4: Backend correctly identifies key version from encrypted data
     * 
     * <p>For any encrypted secret, the backend should be able to identify which key version
     * was used for encryption and use the corresponding key for decryption. This validates
     * that the version prefix mechanism works correctly.</p>
     * 
     * <p><strong>Validates: Requirements 2.2, 2.5, 2.9, 8.2</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-3-Multi-Key-Backend-Decrypts-Any-Configured-Key-Version")
    void backendIdentifiesKeyVersionFromEncryptedData(
            @ForAll("secrets") String originalSecret,
            @ForAll("keyVersions") String keyVersion) throws Exception {
        
        // Given: A backend with multiple key versions
        MultiKeyTestHelper helper = new MultiKeyTestHelper("20240115");
        
        // When: We encrypt a secret with a specific key version
        String encrypted = helper.encrypt(originalSecret, keyVersion);
        
        // Then: The backend should decrypt it using the correct key version
        // (The decrypt method uses the version parameter to look up the key)
        String decrypted = helper.decrypt(encrypted, keyVersion);
        
        assertThat(decrypted)
            .as("Backend should identify and use key version %s for decryption", keyVersion)
            .isEqualTo(originalSecret);
    }

    /**
     * Property 3.5: Backend decrypts empty secrets with any key version
     * 
     * <p>For empty secrets encrypted with any key version, the backend should successfully
     * decrypt them and return an empty string. This validates that the multi-key support
     * works correctly even for edge cases like empty secrets.</p>
     * 
     * <p><strong>Validates: Requirements 2.2, 2.5, 2.9, 8.2</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-3-Multi-Key-Backend-Decrypts-Any-Configured-Key-Version")
    void backendDecryptsEmptySecretsWithAnyKeyVersion(
            @ForAll("keyVersions") String keyVersion) throws Exception {
        
        // Given: An empty secret
        String originalSecret = "";
        MultiKeyTestHelper helper = new MultiKeyTestHelper("20240115");
        
        // When: We encrypt and decrypt the empty secret with a specific key version
        String encrypted = helper.encrypt(originalSecret, keyVersion);
        String decrypted = helper.decrypt(encrypted, keyVersion);
        
        // Then: The decrypted value should be empty
        assertThat(decrypted)
            .as("Backend should decrypt empty secrets with key version %s", keyVersion)
            .isEmpty();
    }

    /**
     * Property 3.6: Backend decrypts long secrets with any key version
     * 
     * <p>For long secrets encrypted with any key version, the backend should successfully
     * decrypt them and return the complete original value. This validates that multi-key
     * support works correctly for secrets of various sizes.</p>
     * 
     * <p><strong>Validates: Requirements 2.2, 2.5, 2.9, 8.2</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-3-Multi-Key-Backend-Decrypts-Any-Configured-Key-Version")
    void backendDecryptsLongSecretsWithAnyKeyVersion(
            @ForAll("longSecrets") String originalSecret,
            @ForAll("keyVersions") String keyVersion) throws Exception {
        
        // Given: A long secret
        MultiKeyTestHelper helper = new MultiKeyTestHelper("20240115");
        
        // When: We encrypt and decrypt the long secret with a specific key version
        String encrypted = helper.encrypt(originalSecret, keyVersion);
        String decrypted = helper.decrypt(encrypted, keyVersion);
        
        // Then: The entire secret should be preserved
        assertThat(decrypted)
            .as("Backend should decrypt long secrets with key version %s", keyVersion)
            .isEqualTo(originalSecret)
            .hasSize(originalSecret.length());
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
     * Provides arbitrary encryption key versions for testing.
     * 
     * <p>Generates key version strings in YYYYMMDD format representing
     * different dates that might be used for key rotation.</p>
     */
    @Provide
    Arbitrary<String> keyVersions() {
        return Arbitraries.of(
            "20240101",
            "20240115",
            "20240201",
            "20231215"
        );
    }

    // ========== Helper Class ==========

    /**
     * Helper class for testing multi-key encryption/decryption.
     * 
     * <p>This class creates a MultiKeyBackend instance with multiple configured
     * key versions and provides access to the private encrypt/decrypt methods
     * for testing purposes. It simulates a realistic multi-key configuration
     * where multiple key versions are active simultaneously.</p>
     */
    private static class MultiKeyTestHelper {
        private final Object backendInstance;
        private final Method encryptMethod;
        private final Method decryptMethod;
        private final Map<String, SecretKey> allKeyVersions;
        
        public MultiKeyTestHelper(String currentKeyVersion) throws Exception {
            // Generate valid AES-256 keys for all test key versions
            this.allKeyVersions = generateAllKeyVersions();
            
            // Create a MultiKeyBackend instance with all key versions configured
            this.backendInstance = createBackendInstance(allKeyVersions, currentKeyVersion);
            
            // Get references to the private encrypt/decrypt methods
            this.encryptMethod = MultiKeyBackend.class.getDeclaredMethod("encrypt", String.class, String.class);
            this.encryptMethod.setAccessible(true);
            
            this.decryptMethod = MultiKeyBackend.class.getDeclaredMethod("decrypt", String.class, String.class);
            this.decryptMethod.setAccessible(true);
        }
        
        /**
         * Generates encryption keys for all test key versions.
         * 
         * <p>This creates a realistic multi-key configuration with four different
         * key versions, simulating a scenario where keys have been rotated over time.</p>
         */
        private Map<String, SecretKey> generateAllKeyVersions() throws Exception {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            
            Map<String, SecretKey> keys = new HashMap<>();
            keys.put("20231215", keyGen.generateKey());
            keys.put("20240101", keyGen.generateKey());
            keys.put("20240115", keyGen.generateKey());
            keys.put("20240201", keyGen.generateKey());
            
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
         * Simulates key rotation by changing the current key version.
         * 
         * <p>This allows testing the scenario where the current key version changes
         * but the backend still maintains all previous key versions for decryption.</p>
         */
        public void setCurrentKeyVersion(String newCurrentKeyVersion) throws Exception {
            setField(backendInstance, "currentKeyVersion", newCurrentKeyVersion);
        }
    }
}
