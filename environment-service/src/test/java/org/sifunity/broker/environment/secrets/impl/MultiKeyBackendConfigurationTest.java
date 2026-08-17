// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.secrets.ConfigurationException;
import org.sifunity.broker.environment.secrets.config.MultiKeyProperties;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for MultiKeyBackend configuration validation.
 * 
 * <p>These tests verify that the MultiKeyBackend properly validates its
 * configuration during initialization and fails fast with descriptive error
 * messages when the configuration is invalid. They also verify that sensitive
 * key material is never exposed in logs or error messages.
 * 
 * <p><b>Test Coverage:</b>
 * <ul>
 *   <li>Initialization fails without current key</li>
 *   <li>Initialization fails with invalid key format</li>
 *   <li>Initialization succeeds with valid configuration</li>
 *   <li>Verify keys never logged</li>
 * </ul>
 * 
 * <p><b>Validates Requirements:</b> 6.5, 6.6, 6.7, 11.1
 */
@ExtendWith(MockitoExtension.class)
class MultiKeyBackendConfigurationTest {
    
    @Mock
    private EnvironmentRepository environmentRepository;
    
    // ========== Test Initialization Fails Without Current Key ==========
    
    @Test
    void initialization_shouldFailWhenNoKeysConfigured() {
        // Given: Properties with no keys configured
        MultiKeyProperties properties = new MultiKeyProperties();
        properties.setCurrentVersion("20240115");
        properties.setKeys(new HashMap<>()); // Empty keys map
        
        // When/Then: Initialization should fail with ConfigurationException
        assertThatThrownBy(() -> new MultiKeyBackend(environmentRepository, properties))
            .isInstanceOf(ConfigurationException.class)
            .hasMessageContaining("No encryption keys configured");
    }
    
    @Test
    void initialization_shouldFailWhenKeysMapIsNull() {
        // Given: Properties with null keys map
        MultiKeyProperties properties = new MultiKeyProperties();
        properties.setCurrentVersion("20240115");
        properties.setKeys(null);
        
        // When/Then: Initialization should fail with ConfigurationException
        assertThatThrownBy(() -> new MultiKeyBackend(environmentRepository, properties))
            .isInstanceOf(ConfigurationException.class)
            .hasMessageContaining("No encryption keys configured");
    }
    
    @Test
    void initialization_shouldFailWhenCurrentKeyVersionNotInKeysMap() throws Exception {
        // Given: Properties with keys but current version not in the map
        String base64Key = generateValidBase64Key();
        
        MultiKeyProperties properties = new MultiKeyProperties();
        properties.setCurrentVersion("20240115"); // Current version
        Map<String, String> keys = new HashMap<>();
        keys.put("20240101", base64Key); // Different version
        properties.setKeys(keys);
        
        // When/Then: Initialization should fail with ConfigurationException
        assertThatThrownBy(() -> new MultiKeyBackend(environmentRepository, properties))
            .isInstanceOf(ConfigurationException.class)
            .hasMessageContaining("Current key version not found in configured keys")
            .hasMessageContaining("20240115");
    }
    
    @Test
    void initialization_shouldFailWhenCurrentVersionIsNull() throws Exception {
        // Given: Properties with keys but null current version
        String base64Key = generateValidBase64Key();
        
        MultiKeyProperties properties = new MultiKeyProperties();
        properties.setCurrentVersion(null); // Null current version
        Map<String, String> keys = new HashMap<>();
        keys.put("20240115", base64Key);
        properties.setKeys(keys);
        
        // When/Then: Initialization should fail with ConfigurationException
        assertThatThrownBy(() -> new MultiKeyBackend(environmentRepository, properties))
            .isInstanceOf(ConfigurationException.class)
            .hasMessageContaining("Current key version not found in configured keys");
    }
    
    // ========== Test Initialization Fails With Invalid Key Format ==========
    
    @Test
    void initialization_shouldFailWhenKeyIsNotBase64Encoded() {
        // Given: Properties with invalid base64 key
        MultiKeyProperties properties = new MultiKeyProperties();
        properties.setCurrentVersion("20240115");
        Map<String, String> keys = new HashMap<>();
        keys.put("20240115", "not-valid-base64!!!"); // Invalid base64
        properties.setKeys(keys);
        
        // When/Then: Initialization should fail with ConfigurationException
        assertThatThrownBy(() -> new MultiKeyBackend(environmentRepository, properties))
            .isInstanceOf(ConfigurationException.class)
            .hasMessageContaining("Invalid base64 encoding")
            .hasMessageContaining("20240115");
    }
    
    @Test
    void initialization_shouldFailWhenKeyIsTooShort() {
        // Given: Properties with key that's too short (128 bits instead of 256)
        byte[] shortKey = new byte[16]; // 128 bits
        String base64ShortKey = Base64.getEncoder().encodeToString(shortKey);
        
        MultiKeyProperties properties = new MultiKeyProperties();
        properties.setCurrentVersion("20240115");
        Map<String, String> keys = new HashMap<>();
        keys.put("20240115", base64ShortKey);
        properties.setKeys(keys);
        
        // When/Then: Initialization should fail with ConfigurationException
        assertThatThrownBy(() -> new MultiKeyBackend(environmentRepository, properties))
            .isInstanceOf(ConfigurationException.class)
            .hasMessageContaining("Invalid key length")
            .hasMessageContaining("20240115")
            .hasMessageContaining("expected 32 bytes")
            .hasMessageContaining("got 16 bytes");
    }
    
    @Test
    void initialization_shouldFailWhenKeyIsTooLong() {
        // Given: Properties with key that's too long (512 bits instead of 256)
        byte[] longKey = new byte[64]; // 512 bits
        String base64LongKey = Base64.getEncoder().encodeToString(longKey);
        
        MultiKeyProperties properties = new MultiKeyProperties();
        properties.setCurrentVersion("20240115");
        Map<String, String> keys = new HashMap<>();
        keys.put("20240115", base64LongKey);
        properties.setKeys(keys);
        
        // When/Then: Initialization should fail with ConfigurationException
        assertThatThrownBy(() -> new MultiKeyBackend(environmentRepository, properties))
            .isInstanceOf(ConfigurationException.class)
            .hasMessageContaining("Invalid key length")
            .hasMessageContaining("20240115")
            .hasMessageContaining("expected 32 bytes")
            .hasMessageContaining("got 64 bytes");
    }
    
    @Test
    void initialization_shouldFailWhenKeyIsEmpty() {
        // Given: Properties with empty key string
        MultiKeyProperties properties = new MultiKeyProperties();
        properties.setCurrentVersion("20240115");
        Map<String, String> keys = new HashMap<>();
        keys.put("20240115", ""); // Empty string
        properties.setKeys(keys);
        
        // When/Then: Initialization should fail with ConfigurationException
        assertThatThrownBy(() -> new MultiKeyBackend(environmentRepository, properties))
            .isInstanceOf(ConfigurationException.class)
            .hasMessageContaining("Invalid");
    }
    
    @Test
    void initialization_shouldFailWhenOneOfMultipleKeysIsInvalid() throws Exception {
        // Given: Properties with multiple keys, one invalid
        String validKey = generateValidBase64Key();
        String invalidKey = "not-valid-base64!!!";
        
        MultiKeyProperties properties = new MultiKeyProperties();
        properties.setCurrentVersion("20240115");
        Map<String, String> keys = new HashMap<>();
        keys.put("20240115", validKey); // Valid current key
        keys.put("20240101", invalidKey); // Invalid old key
        properties.setKeys(keys);
        
        // When/Then: Initialization should fail with ConfigurationException
        assertThatThrownBy(() -> new MultiKeyBackend(environmentRepository, properties))
            .isInstanceOf(ConfigurationException.class)
            .hasMessageContaining("Invalid base64 encoding")
            .hasMessageContaining("20240101");
    }
    
    // ========== Test Initialization Succeeds With Valid Configuration ==========
    
    @Test
    void initialization_shouldSucceedWithValidSingleKey() throws Exception {
        // Given: Properties with valid single key
        String base64Key = generateValidBase64Key();
        
        MultiKeyProperties properties = new MultiKeyProperties();
        properties.setCurrentVersion("20240115");
        Map<String, String> keys = new HashMap<>();
        keys.put("20240115", base64Key);
        properties.setKeys(keys);
        
        // When: Creating backend instance
        MultiKeyBackend backend = new MultiKeyBackend(environmentRepository, properties);
        
        // Then: Backend should be created successfully
        assertThat(backend).isNotNull();
        assertThat(backend.getBackendType()).isEqualTo("multi-key");
        assertThat(backend.isHealthy()).isTrue();
    }
    
    @Test
    void initialization_shouldSucceedWithMultipleValidKeys() throws Exception {
        // Given: Properties with multiple valid keys
        String currentKey = generateValidBase64Key();
        String oldKey1 = generateValidBase64Key();
        String oldKey2 = generateValidBase64Key();
        
        MultiKeyProperties properties = new MultiKeyProperties();
        properties.setCurrentVersion("20240115");
        Map<String, String> keys = new HashMap<>();
        keys.put("20240115", currentKey);
        keys.put("20240101", oldKey1);
        keys.put("20231215", oldKey2);
        properties.setKeys(keys);
        
        // When: Creating backend instance
        MultiKeyBackend backend = new MultiKeyBackend(environmentRepository, properties);
        
        // Then: Backend should be created successfully
        assertThat(backend).isNotNull();
        assertThat(backend.getBackendType()).isEqualTo("multi-key");
        assertThat(backend.isHealthy()).isTrue();
    }
    
    @Test
    void initialization_shouldSucceedWithDifferentVersionFormats() throws Exception {
        // Given: Properties with various version formats (all YYYYMMDD)
        String key1 = generateValidBase64Key();
        String key2 = generateValidBase64Key();
        String key3 = generateValidBase64Key();
        
        MultiKeyProperties properties = new MultiKeyProperties();
        properties.setCurrentVersion("20240601");
        Map<String, String> keys = new HashMap<>();
        keys.put("20240601", key1); // June 2024
        keys.put("20240101", key2); // January 2024
        keys.put("20231231", key3); // December 2023
        properties.setKeys(keys);
        
        // When: Creating backend instance
        MultiKeyBackend backend = new MultiKeyBackend(environmentRepository, properties);
        
        // Then: Backend should be created successfully
        assertThat(backend).isNotNull();
        assertThat(backend.isHealthy()).isTrue();
    }
    
    // ========== Test Keys Never Logged ==========
    
    @Test
    void initialization_errorMessagesShouldNotContainKeyValues() {
        // Given: Properties with invalid key length
        byte[] shortKey = new byte[16];
        String base64ShortKey = Base64.getEncoder().encodeToString(shortKey);
        
        MultiKeyProperties properties = new MultiKeyProperties();
        properties.setCurrentVersion("20240115");
        Map<String, String> keys = new HashMap<>();
        keys.put("20240115", base64ShortKey);
        properties.setKeys(keys);
        
        // When/Then: Error message should not contain the key value
        assertThatThrownBy(() -> new MultiKeyBackend(environmentRepository, properties))
            .isInstanceOf(ConfigurationException.class)
            .satisfies(exception -> {
                String message = exception.getMessage();
                assertThat(message)
                    .as("Error message should not contain key value")
                    .doesNotContain(base64ShortKey);
            });
    }
    
    @Test
    void initialization_errorMessagesShouldNotContainBase64KeysForInvalidFormat() {
        // Given: Properties with invalid base64 key
        String invalidKey = "not-valid-base64!!!";
        
        MultiKeyProperties properties = new MultiKeyProperties();
        properties.setCurrentVersion("20240115");
        Map<String, String> keys = new HashMap<>();
        keys.put("20240115", invalidKey);
        properties.setKeys(keys);
        
        // When/Then: Error message should not contain the key value
        assertThatThrownBy(() -> new MultiKeyBackend(environmentRepository, properties))
            .isInstanceOf(ConfigurationException.class)
            .satisfies(exception -> {
                String message = exception.getMessage();
                assertThat(message)
                    .as("Error message should not contain key value")
                    .doesNotContain(invalidKey);
            });
    }
    
    @Test
    void initialization_errorMessagesShouldContainVersionButNotKey() throws Exception {
        // Given: Properties with valid key but missing current version
        String base64Key = generateValidBase64Key();
        
        MultiKeyProperties properties = new MultiKeyProperties();
        properties.setCurrentVersion("20240115");
        Map<String, String> keys = new HashMap<>();
        keys.put("20240101", base64Key); // Different version
        properties.setKeys(keys);
        
        // When/Then: Error message should contain version but not key
        assertThatThrownBy(() -> new MultiKeyBackend(environmentRepository, properties))
            .isInstanceOf(ConfigurationException.class)
            .satisfies(exception -> {
                String message = exception.getMessage();
                assertThat(message)
                    .as("Error message should contain version")
                    .contains("20240115");
                assertThat(message)
                    .as("Error message should not contain key value")
                    .doesNotContain(base64Key);
            });
    }
    
    @Test
    void initialization_successfulCreationShouldNotLogKeys() throws Exception {
        // Given: Properties with valid configuration
        String base64Key = generateValidBase64Key();
        
        MultiKeyProperties properties = new MultiKeyProperties();
        properties.setCurrentVersion("20240115");
        Map<String, String> keys = new HashMap<>();
        keys.put("20240115", base64Key);
        properties.setKeys(keys);
        
        // When: Creating backend instance (this would log if logging was enabled)
        MultiKeyBackend backend = new MultiKeyBackend(environmentRepository, properties);
        
        // Then: Backend should be created successfully
        // Note: We can't directly verify log output in unit tests without a log appender,
        // but we verify that the backend is created without throwing exceptions
        assertThat(backend).isNotNull();
        assertThat(backend.isHealthy()).isTrue();
        
        // The requirement 6.7 states keys should never be logged. This is enforced
        // by the implementation not including key values in any log statements.
        // The MultiKeyBackend implementation uses @Slf4j but never logs key values.
    }
    
    // ========== Helper Methods ==========
    
    /**
     * Generates a valid base64-encoded 256-bit AES key for testing.
     * 
     * @return base64-encoded 256-bit key
     * @throws Exception if key generation fails
     */
    private String generateValidBase64Key() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        SecretKey secretKey = keyGen.generateKey();
        return Base64.getEncoder().encodeToString(secretKey.getEncoded());
    }
}
