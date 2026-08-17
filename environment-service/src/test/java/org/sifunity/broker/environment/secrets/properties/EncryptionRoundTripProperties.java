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
 * Property-based tests for encryption round-trip preservation.
 * 
 * <p>These tests validate that encrypting a secret and then immediately decrypting it
 * produces the original secret value unchanged. This is a fundamental correctness
 * property for any encryption implementation.</p>
 * 
 * <p><strong>Validates: Requirements 2.10, 11.4</strong></p>
 * 
 * <p><strong>Property 1: Encryption Round-Trip Preservation</strong></p>
 * <p>For any valid secret string and any configured encryption key, encrypting the
 * secret then immediately decrypting it should produce the original secret value unchanged.</p>
 */
@Tag("pluggable-secrets-backend")
class EncryptionRoundTripProperties {

    // ========== Property 1: Encryption Round-Trip Preservation ==========

    /**
     * Property 1.1: Encryption round-trip preserves secret value
     * 
     * <p>For any valid secret string, encrypting it with the MultiKeyBackend and then
     * immediately decrypting it should produce the original secret value unchanged.</p>
     * 
     * <p>This property validates the fundamental correctness of the encryption/decryption
     * implementation. If this property fails, it indicates a critical bug in the
     * cryptographic implementation.</p>
     * 
     * <p><strong>Validates: Requirements 2.10, 11.4</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-1-Encryption-Round-Trip-Preservation")
    void encryptionRoundTripPreservesSecretValue(
            @ForAll("secrets") String originalSecret,
            @ForAll("encryptionKeys") String keyVersion) throws Exception {
        
        // Given: A MultiKeyBackend with a configured encryption key
        EncryptionTestHelper helper = new EncryptionTestHelper(keyVersion);
        
        // When: We encrypt the secret and then decrypt it
        String encrypted = helper.encrypt(originalSecret, keyVersion);
        String decrypted = helper.decrypt(encrypted, keyVersion);
        
        // Then: The decrypted value should match the original secret exactly
        assertThat(decrypted)
            .as("Decrypted secret should match original secret")
            .isEqualTo(originalSecret);
    }

    /**
     * Property 1.2: Encryption round-trip preserves empty secrets
     * 
     * <p>For empty string secrets, the encryption round-trip should preserve
     * the empty value.</p>
     * 
     * <p><strong>Validates: Requirements 2.10, 11.4</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-1-Encryption-Round-Trip-Preservation")
    void encryptionRoundTripPreservesEmptySecrets(
            @ForAll("encryptionKeys") String keyVersion) throws Exception {
        
        // Given: An empty secret
        String originalSecret = "";
        EncryptionTestHelper helper = new EncryptionTestHelper(keyVersion);
        
        // When: We encrypt and decrypt the empty secret
        String encrypted = helper.encrypt(originalSecret, keyVersion);
        String decrypted = helper.decrypt(encrypted, keyVersion);
        
        // Then: The decrypted value should be empty
        assertThat(decrypted)
            .as("Decrypted empty secret should remain empty")
            .isEmpty();
    }

    /**
     * Property 1.3: Encryption round-trip preserves secrets with special characters
     * 
     * <p>For secrets containing special characters (Unicode, symbols, whitespace),
     * the encryption round-trip should preserve all characters exactly.</p>
     * 
     * <p><strong>Validates: Requirements 2.10, 11.4</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-1-Encryption-Round-Trip-Preservation")
    void encryptionRoundTripPreservesSpecialCharacters(
            @ForAll("secretsWithSpecialChars") String originalSecret,
            @ForAll("encryptionKeys") String keyVersion) throws Exception {
        
        // Given: A secret with special characters
        EncryptionTestHelper helper = new EncryptionTestHelper(keyVersion);
        
        // When: We encrypt and decrypt the secret
        String encrypted = helper.encrypt(originalSecret, keyVersion);
        String decrypted = helper.decrypt(encrypted, keyVersion);
        
        // Then: All special characters should be preserved
        assertThat(decrypted)
            .as("Decrypted secret should preserve all special characters")
            .isEqualTo(originalSecret);
    }

    /**
     * Property 1.4: Encryption round-trip preserves long secrets
     * 
     * <p>For secrets of various lengths (up to 1000 characters), the encryption
     * round-trip should preserve the entire value.</p>
     * 
     * <p><strong>Validates: Requirements 2.10, 11.4</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-1-Encryption-Round-Trip-Preservation")
    void encryptionRoundTripPreservesLongSecrets(
            @ForAll("longSecrets") String originalSecret,
            @ForAll("encryptionKeys") String keyVersion) throws Exception {
        
        // Given: A long secret
        EncryptionTestHelper helper = new EncryptionTestHelper(keyVersion);
        
        // When: We encrypt and decrypt the long secret
        String encrypted = helper.encrypt(originalSecret, keyVersion);
        String decrypted = helper.decrypt(encrypted, keyVersion);
        
        // Then: The entire secret should be preserved
        assertThat(decrypted)
            .as("Decrypted long secret should match original")
            .isEqualTo(originalSecret)
            .hasSize(originalSecret.length());
    }

    /**
     * Property 1.5: Multiple encryptions produce different ciphertexts
     * 
     * <p>For the same secret encrypted multiple times, each encryption should
     * produce a different ciphertext (due to random IV generation), but all
     * should decrypt to the original value.</p>
     * 
     * <p>This validates that the IV is properly randomized for each encryption.</p>
     * 
     * <p><strong>Validates: Requirements 2.10, 11.4</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-1-Encryption-Round-Trip-Preservation")
    void multipleEncryptionsProduceDifferentCiphertexts(
            @ForAll("secrets") String originalSecret,
            @ForAll("encryptionKeys") String keyVersion) throws Exception {
        
        // Given: A MultiKeyBackend with a configured encryption key
        EncryptionTestHelper helper = new EncryptionTestHelper(keyVersion);
        
        // When: We encrypt the same secret twice
        String encrypted1 = helper.encrypt(originalSecret, keyVersion);
        String encrypted2 = helper.encrypt(originalSecret, keyVersion);
        
        // Then: The ciphertexts should be different (due to random IV)
        assertThat(encrypted1)
            .as("Multiple encryptions should produce different ciphertexts")
            .isNotEqualTo(encrypted2);
        
        // But: Both should decrypt to the original secret
        String decrypted1 = helper.decrypt(encrypted1, keyVersion);
        String decrypted2 = helper.decrypt(encrypted2, keyVersion);
        
        assertThat(decrypted1)
            .as("First decryption should match original")
            .isEqualTo(originalSecret);
        assertThat(decrypted2)
            .as("Second decryption should match original")
            .isEqualTo(originalSecret);
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
     * Provides arbitrary secret strings with special characters.
     * 
     * <p>Generates strings that include common special characters, whitespace,
     * and symbols to test edge cases. Uses a controlled set of characters that
     * are valid in secrets.</p>
     */
    @Provide
    Arbitrary<String> secretsWithSpecialChars() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .whitespace()
            .withChars("!@#$%^&*()_+-=[]{}|;:,.<>?/~`'\"\\")
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
     * <p>Generates key version strings in YYYYMMDD format.</p>
     */
    @Provide
    Arbitrary<String> encryptionKeys() {
        return Arbitraries.of(
            "20240101",
            "20240115",
            "20240201",
            "20231215"
        );
    }

    // ========== Helper Class ==========

    /**
     * Helper class for testing encryption/decryption methods.
     * 
     * <p>This class creates a minimal MultiKeyBackend instance and provides
     * access to the private encrypt/decrypt methods for testing purposes.</p>
     */
    private static class EncryptionTestHelper {
        private final Object backendInstance;
        private final Method encryptMethod;
        private final Method decryptMethod;
        
        public EncryptionTestHelper(String keyVersion) throws Exception {
            // Generate a valid AES-256 key
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            SecretKey secretKey = keyGen.generateKey();
            
            // Create a map of key versions
            Map<String, SecretKey> keyVersions = new HashMap<>();
            keyVersions.put(keyVersion, secretKey);
            
            // Create a MultiKeyBackend instance and inject fields using reflection
            this.backendInstance = createBackendInstance(keyVersions, keyVersion);
            
            // Get references to the private encrypt/decrypt methods
            this.encryptMethod = MultiKeyBackend.class.getDeclaredMethod("encrypt", String.class, String.class);
            this.encryptMethod.setAccessible(true);
            
            this.decryptMethod = MultiKeyBackend.class.getDeclaredMethod("decrypt", String.class, String.class);
            this.decryptMethod.setAccessible(true);
        }
        
        private Object createBackendInstance(Map<String, SecretKey> keyVersions, String currentKeyVersion) throws Exception {
            // Use sun.misc.Unsafe to create an instance without calling the constructor
            // This bypasses the loadKeys method which is not yet implemented
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
    }
}
