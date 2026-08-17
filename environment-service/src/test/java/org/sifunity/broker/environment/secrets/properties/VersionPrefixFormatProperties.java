// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.properties;

import net.jqwik.api.*;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.secrets.config.MultiKeyProperties;
import org.sifunity.broker.environment.secrets.impl.MultiKeyBackend;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for version prefix format validation.
 * 
 * <p>These tests validate that any secret stored by the Multi-Key Backend
 * is stored with the correct version prefix format "YYYYMMDD:encrypted_data"
 * where YYYYMMDD is the current key version and encrypted_data is the
 * base64-encoded encrypted secret.</p>
 * 
 * <p><strong>Validates: Requirements 2.3, 2.4</strong></p>
 * 
 * <p><strong>Property 2: Multi-Key Backend Version Prefix Format</strong></p>
 * <p>For any secret stored by the Multi-Key Backend, the stored value in the
 * database should match the format "YYYYMMDD:base64_data" where YYYYMMDD is
 * the current key version and base64_data is the encrypted secret.</p>
 */
@Tag("pluggable-secrets-backend")
class VersionPrefixFormatProperties {

    // Pattern for YYYYMMDD:base64_data format
    // YYYYMMDD = 8 digits
    // : = literal colon
    // base64_data = one or more base64 characters (A-Z, a-z, 0-9, +, /, =)
    private static final Pattern VERSION_PREFIX_PATTERN = 
        Pattern.compile("^\\d{8}:[A-Za-z0-9+/]+=*$");

    // ========== Property 2: Multi-Key Backend Version Prefix Format ==========

    /**
     * Property 2.1: Stored value matches version prefix format
     * 
     * <p>For any valid secret string stored by the MultiKeyBackend, the value
     * stored in the database should match the format "YYYYMMDD:base64_data".</p>
     * 
     * <p>This property validates that the backend correctly prefixes encrypted
     * values with the key version in the expected format.</p>
     * 
     * <p><strong>Validates: Requirements 2.3, 2.4</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-2-Version-Prefix-Format")
    void storedValueMatchesVersionPrefixFormat(
            @ForAll("secrets") String secret,
            @ForAll("keyVersions") String keyVersion) throws Exception {
        
        // Given: A MultiKeyBackend with a configured encryption key
        BackendTestHelper helper = new BackendTestHelper(keyVersion);
        UUID environmentId = UUID.randomUUID();
        
        // When: We store a secret
        String storedValue = helper.storeAndGetStoredValue(environmentId, secret);
        
        // Then: The stored value should match the version prefix format
        assertThat(storedValue)
            .as("Stored value should match YYYYMMDD:base64_data format")
            .matches(VERSION_PREFIX_PATTERN);
    }

    /**
     * Property 2.2: Stored value starts with current key version
     * 
     * <p>For any secret stored by the MultiKeyBackend, the stored value
     * should start with the current key version followed by a colon.</p>
     * 
     * <p><strong>Validates: Requirements 2.3, 2.4</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-2-Version-Prefix-Format")
    void storedValueStartsWithCurrentKeyVersion(
            @ForAll("secrets") String secret,
            @ForAll("keyVersions") String keyVersion) throws Exception {
        
        // Given: A MultiKeyBackend with a configured encryption key
        BackendTestHelper helper = new BackendTestHelper(keyVersion);
        UUID environmentId = UUID.randomUUID();
        
        // When: We store a secret
        String storedValue = helper.storeAndGetStoredValue(environmentId, secret);
        
        // Then: The stored value should start with the current key version
        assertThat(storedValue)
            .as("Stored value should start with current key version")
            .startsWith(keyVersion + ":");
    }

    /**
     * Property 2.3: Version prefix is exactly 8 digits
     * 
     * <p>For any secret stored by the MultiKeyBackend, the version prefix
     * (before the colon) should be exactly 8 digits in YYYYMMDD format.</p>
     * 
     * <p><strong>Validates: Requirements 2.3, 2.4</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-2-Version-Prefix-Format")
    void versionPrefixIsExactlyEightDigits(
            @ForAll("secrets") String secret,
            @ForAll("keyVersions") String keyVersion) throws Exception {
        
        // Given: A MultiKeyBackend with a configured encryption key
        BackendTestHelper helper = new BackendTestHelper(keyVersion);
        UUID environmentId = UUID.randomUUID();
        
        // When: We store a secret
        String storedValue = helper.storeAndGetStoredValue(environmentId, secret);
        
        // Then: The version prefix should be exactly 8 digits
        String[] parts = storedValue.split(":", 2);
        assertThat(parts)
            .as("Stored value should have version and encrypted data parts")
            .hasSize(2);
        
        assertThat(parts[0])
            .as("Version prefix should be exactly 8 digits")
            .hasSize(8)
            .matches("\\d{8}");
    }

    /**
     * Property 2.4: Encrypted data is valid base64
     * 
     * <p>For any secret stored by the MultiKeyBackend, the encrypted data
     * portion (after the colon) should be valid base64-encoded data.</p>
     * 
     * <p><strong>Validates: Requirements 2.3, 2.4</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-2-Version-Prefix-Format")
    void encryptedDataIsValidBase64(
            @ForAll("secrets") String secret,
            @ForAll("keyVersions") String keyVersion) throws Exception {
        
        // Given: A MultiKeyBackend with a configured encryption key
        BackendTestHelper helper = new BackendTestHelper(keyVersion);
        UUID environmentId = UUID.randomUUID();
        
        // When: We store a secret
        String storedValue = helper.storeAndGetStoredValue(environmentId, secret);
        
        // Then: The encrypted data should be valid base64
        String[] parts = storedValue.split(":", 2);
        String encryptedData = parts[1];
        
        assertThat(encryptedData)
            .as("Encrypted data should be valid base64")
            .matches("[A-Za-z0-9+/]+=*");
        
        // Verify it can be decoded without exception
        try {
            Base64.getDecoder().decode(encryptedData);
        } catch (IllegalArgumentException e) {
            throw new AssertionError("Encrypted data is not valid base64: " + encryptedData, e);
        }
    }

    /**
     * Property 2.5: Empty secrets produce valid format
     * 
     * <p>For empty string secrets, the stored value should still match
     * the version prefix format.</p>
     * 
     * <p><strong>Validates: Requirements 2.3, 2.4</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-2-Version-Prefix-Format")
    void emptySecretsProduceValidFormat(
            @ForAll("keyVersions") String keyVersion) throws Exception {
        
        // Given: An empty secret
        String secret = "";
        BackendTestHelper helper = new BackendTestHelper(keyVersion);
        UUID environmentId = UUID.randomUUID();
        
        // When: We store the empty secret
        String storedValue = helper.storeAndGetStoredValue(environmentId, secret);
        
        // Then: The stored value should still match the format
        assertThat(storedValue)
            .as("Empty secret should produce valid version prefix format")
            .matches(VERSION_PREFIX_PATTERN)
            .startsWith(keyVersion + ":");
    }

    /**
     * Property 2.6: Long secrets produce valid format
     * 
     * <p>For long secrets (up to 1000 characters), the stored value should
     * still match the version prefix format.</p>
     * 
     * <p><strong>Validates: Requirements 2.3, 2.4</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-2-Version-Prefix-Format")
    void longSecretsProduceValidFormat(
            @ForAll("longSecrets") String secret,
            @ForAll("keyVersions") String keyVersion) throws Exception {
        
        // Given: A long secret
        BackendTestHelper helper = new BackendTestHelper(keyVersion);
        UUID environmentId = UUID.randomUUID();
        
        // When: We store the long secret
        String storedValue = helper.storeAndGetStoredValue(environmentId, secret);
        
        // Then: The stored value should match the format
        assertThat(storedValue)
            .as("Long secret should produce valid version prefix format")
            .matches(VERSION_PREFIX_PATTERN)
            .startsWith(keyVersion + ":");
    }

    /**
     * Property 2.7: Special characters produce valid format
     * 
     * <p>For secrets containing special characters, the stored value should
     * still match the version prefix format with valid base64 encoding.</p>
     * 
     * <p><strong>Validates: Requirements 2.3, 2.4</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-2-Version-Prefix-Format")
    void specialCharactersProduceValidFormat(
            @ForAll("secretsWithSpecialChars") String secret,
            @ForAll("keyVersions") String keyVersion) throws Exception {
        
        // Given: A secret with special characters
        BackendTestHelper helper = new BackendTestHelper(keyVersion);
        UUID environmentId = UUID.randomUUID();
        
        // When: We store the secret
        String storedValue = helper.storeAndGetStoredValue(environmentId, secret);
        
        // Then: The stored value should match the format
        assertThat(storedValue)
            .as("Secret with special characters should produce valid version prefix format")
            .matches(VERSION_PREFIX_PATTERN)
            .startsWith(keyVersion + ":");
    }

    /**
     * Property 2.8: Multiple stores produce same version prefix
     * 
     * <p>For the same secret stored multiple times, all stored values should
     * have the same version prefix (though the encrypted data will differ
     * due to random IV generation).</p>
     * 
     * <p><strong>Validates: Requirements 2.3, 2.4</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-2-Version-Prefix-Format")
    void multipleStoresProduceSameVersionPrefix(
            @ForAll("secrets") String secret,
            @ForAll("keyVersions") String keyVersion) throws Exception {
        
        // Given: A MultiKeyBackend with a configured encryption key
        BackendTestHelper helper = new BackendTestHelper(keyVersion);
        UUID environmentId1 = UUID.randomUUID();
        UUID environmentId2 = UUID.randomUUID();
        
        // When: We store the same secret twice
        String storedValue1 = helper.storeAndGetStoredValue(environmentId1, secret);
        String storedValue2 = helper.storeAndGetStoredValue(environmentId2, secret);
        
        // Then: Both should have the same version prefix
        String[] parts1 = storedValue1.split(":", 2);
        String[] parts2 = storedValue2.split(":", 2);
        
        assertThat(parts1[0])
            .as("Both stored values should have the same version prefix")
            .isEqualTo(parts2[0])
            .isEqualTo(keyVersion);
        
        // But the encrypted data should be different (due to random IV)
        assertThat(parts1[1])
            .as("Encrypted data should differ due to random IV")
            .isNotEqualTo(parts2[1]);
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
     * and symbols to test edge cases.</p>
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
    Arbitrary<String> keyVersions() {
        return Arbitraries.of(
            "20240101",
            "20240115",
            "20240201",
            "20231215",
            "20230601"
        );
    }

    // ========== Helper Class ==========

    /**
     * Helper class for testing the store method and capturing stored values.
     * 
     * <p>This class creates a MultiKeyBackend instance with a mock repository
     * and provides methods to store secrets and retrieve the stored values.</p>
     */
    private static class BackendTestHelper {
        private final MultiKeyBackend backend;
        private final EnvironmentRepository mockRepository;
        private final String keyVersion;
        
        public BackendTestHelper(String keyVersion) throws Exception {
            this.keyVersion = keyVersion;
            
            // Generate a valid AES-256 key
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            SecretKey secretKey = keyGen.generateKey();
            
            // Create mock repository
            this.mockRepository = mock(EnvironmentRepository.class);
            
            // Create backend instance using reflection to bypass constructor
            this.backend = createBackendInstance(secretKey, keyVersion);
        }
        
        private MultiKeyBackend createBackendInstance(SecretKey secretKey, String keyVersion) throws Exception {
            // Use sun.misc.Unsafe to create an instance without calling the constructor
            // This bypasses the loadKeys method which is not yet implemented
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Object unsafe = unsafeField.get(null);
            java.lang.reflect.Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
            MultiKeyBackend instance = (MultiKeyBackend) allocateInstance.invoke(unsafe, MultiKeyBackend.class);
            
            // Set the required fields using reflection
            Map<String, SecretKey> keyVersionsMap = new HashMap<>();
            keyVersionsMap.put(keyVersion, secretKey);
            
            setField(instance, "keyVersions", keyVersionsMap);
            setField(instance, "currentKeyVersion", keyVersion);
            setField(instance, "secureRandom", new java.security.SecureRandom());
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
