// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.secrets.config.MultiKeyProperties;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.lang.reflect.Method;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for the decrypt method in MultiKeyBackend.
 * 
 * <p>These tests verify that the decrypt method correctly:
 * <ul>
 *   <li>Decrypts data encrypted by the encrypt method (round-trip)</li>
 *   <li>Extracts IV and ciphertext correctly</li>
 *   <li>Uses the correct key version</li>
 *   <li>Throws appropriate exceptions for invalid inputs</li>
 * </ul>
 */
class MultiKeyBackendDecryptTest {
    
    private MultiKeyBackend backend;
    private Method encryptMethod;
    private Method decryptMethod;
    private static final String KEY_VERSION = "20240115";
    
    @BeforeEach
    void setUp() throws Exception {
        // Generate a valid 256-bit AES key
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        SecretKey secretKey = keyGen.generateKey();
        String base64Key = Base64.getEncoder().encodeToString(secretKey.getEncoded());
        
        // Create properties with the test key
        MultiKeyProperties properties = new MultiKeyProperties();
        properties.setCurrentVersion(KEY_VERSION);
        Map<String, String> keys = new HashMap<>();
        keys.put(KEY_VERSION, base64Key);
        properties.setKeys(keys);
        
        // Create backend instance
        EnvironmentRepository mockRepo = mock(EnvironmentRepository.class);
        backend = new MultiKeyBackend(mockRepo, properties);
        
        // Get access to private encrypt and decrypt methods via reflection
        encryptMethod = MultiKeyBackend.class.getDeclaredMethod("encrypt", String.class, String.class);
        encryptMethod.setAccessible(true);
        
        decryptMethod = MultiKeyBackend.class.getDeclaredMethod("decrypt", String.class, String.class);
        decryptMethod.setAccessible(true);
    }
    
    @Test
    void decrypt_shouldDecryptEncryptedSecret() throws Exception {
        // Given
        String originalSecret = "my-secret-password-123";
        
        // When - encrypt then decrypt
        String encrypted = (String) encryptMethod.invoke(backend, originalSecret, KEY_VERSION);
        String decrypted = (String) decryptMethod.invoke(backend, encrypted, KEY_VERSION);
        
        // Then
        assertThat(decrypted).isEqualTo(originalSecret);
    }
    
    @Test
    void decrypt_shouldHandleEmptyString() throws Exception {
        // Given
        String originalSecret = "";
        
        // When - encrypt then decrypt
        String encrypted = (String) encryptMethod.invoke(backend, originalSecret, KEY_VERSION);
        String decrypted = (String) decryptMethod.invoke(backend, encrypted, KEY_VERSION);
        
        // Then
        assertThat(decrypted).isEqualTo(originalSecret);
    }
    
    @Test
    void decrypt_shouldHandleSpecialCharacters() throws Exception {
        // Given
        String originalSecret = "!@#$%^&*()_+-=[]{}|;':\",./<>?`~";
        
        // When - encrypt then decrypt
        String encrypted = (String) encryptMethod.invoke(backend, originalSecret, KEY_VERSION);
        String decrypted = (String) decryptMethod.invoke(backend, encrypted, KEY_VERSION);
        
        // Then
        assertThat(decrypted).isEqualTo(originalSecret);
    }
    
    @Test
    void decrypt_shouldHandleUnicodeCharacters() throws Exception {
        // Given
        String originalSecret = "Hello 世界 🌍 Привет";
        
        // When - encrypt then decrypt
        String encrypted = (String) encryptMethod.invoke(backend, originalSecret, KEY_VERSION);
        String decrypted = (String) decryptMethod.invoke(backend, encrypted, KEY_VERSION);
        
        // Then
        assertThat(decrypted).isEqualTo(originalSecret);
    }
    
    @Test
    void decrypt_shouldHandleLongSecrets() throws Exception {
        // Given - a secret longer than typical block size
        String originalSecret = "a".repeat(1000);
        
        // When - encrypt then decrypt
        String encrypted = (String) encryptMethod.invoke(backend, originalSecret, KEY_VERSION);
        String decrypted = (String) decryptMethod.invoke(backend, encrypted, KEY_VERSION);
        
        // Then
        assertThat(decrypted).isEqualTo(originalSecret);
    }
    
    @Test
    void decrypt_shouldThrowExceptionForInvalidKeyVersion() throws Exception {
        // Given
        String originalSecret = "my-secret";
        String encrypted = (String) encryptMethod.invoke(backend, originalSecret, KEY_VERSION);
        
        // When/Then - decrypt with non-existent key version
        assertThatThrownBy(() -> decryptMethod.invoke(backend, encrypted, "20240101"))
            .isInstanceOf(java.lang.reflect.InvocationTargetException.class)
            .hasCauseInstanceOf(IllegalStateException.class)
            .hasRootCauseMessage("Key version not found: 20240101");
    }
    
    @Test
    void decrypt_shouldThrowExceptionForInvalidBase64() throws Exception {
        // Given - invalid base64 string
        String invalidEncrypted = "not-valid-base64!!!";
        
        // When/Then
        assertThatThrownBy(() -> decryptMethod.invoke(backend, invalidEncrypted, KEY_VERSION))
            .hasCauseInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    void decrypt_shouldThrowExceptionForTamperedData() throws Exception {
        // Given
        String originalSecret = "my-secret";
        String encrypted = (String) encryptMethod.invoke(backend, originalSecret, KEY_VERSION);
        
        // Tamper with the encrypted data by changing one character
        byte[] encryptedBytes = Base64.getDecoder().decode(encrypted);
        encryptedBytes[encryptedBytes.length - 1] ^= 1; // Flip one bit in the auth tag
        String tamperedEncrypted = Base64.getEncoder().encodeToString(encryptedBytes);
        
        // When/Then - decryption should fail due to authentication tag mismatch
        assertThatThrownBy(() -> decryptMethod.invoke(backend, tamperedEncrypted, KEY_VERSION))
            .hasCauseInstanceOf(javax.crypto.AEADBadTagException.class);
    }
}
