// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.secrets.EncryptionException;
import org.sifunity.broker.environment.secrets.SecretNotFoundException;
import org.sifunity.broker.environment.secrets.config.MultiKeyProperties;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.lang.reflect.Field;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the retrieve method in MultiKeyBackend.
 * 
 * <p>These tests verify that the retrieve method correctly:
 * <ul>
 *   <li>Loads Environment entity from repository</li>
 *   <li>Extracts stored value from sharedSecret field</li>
 *   <li>Handles legacy plaintext values (no colon) with warning log</li>
 *   <li>Splits versioned value on colon to extract version and encrypted data</li>
 *   <li>Decrypts using version-specific key</li>
 *   <li>Returns plaintext secret</li>
 * </ul>
 * 
 * <p><b>Validates Requirements:</b> 2.5, 2.9, 10.1
 */
@ExtendWith(MockitoExtension.class)
class MultiKeyBackendRetrieveTest {
    
    @Mock
    private EnvironmentRepository environmentRepository;
    
    private MultiKeyBackend backend;
    private static final String KEY_VERSION = "20240115";
    private UUID testEnvironmentId;
    
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
        backend = new MultiKeyBackend(environmentRepository, properties);
        
        // Manually set the keyVersions map using reflection to bypass loadKeys
        Field keyVersionsField = MultiKeyBackend.class.getDeclaredField("keyVersions");
        keyVersionsField.setAccessible(true);
        Map<String, SecretKey> keyVersionsMap = new HashMap<>();
        keyVersionsMap.put(KEY_VERSION, secretKey);
        keyVersionsField.set(backend, keyVersionsMap);
        
        testEnvironmentId = UUID.randomUUID();
    }
    
    @Test
    void retrieve_shouldDecryptAndReturnSecret() {
        // Given
        String originalSecret = "my-secret-password-123";
        Environment environment = new Environment();
        environment.setId(testEnvironmentId);
        
        // First store the secret
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.save(any(Environment.class))).thenReturn(environment);
        backend.store(testEnvironmentId, originalSecret);
        
        // Now retrieve it
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.of(environment));
        
        // When
        String retrievedSecret = backend.retrieve(testEnvironmentId);
        
        // Then
        assertThat(retrievedSecret).isEqualTo(originalSecret);
        verify(environmentRepository, times(2)).findById(testEnvironmentId);
    }
    
    @Test
    void retrieve_shouldHandleLegacyPlaintextValues() {
        // Given - environment with plaintext secret (no version prefix)
        String plaintextSecret = "legacy-plaintext-secret";
        Environment environment = new Environment();
        environment.setId(testEnvironmentId);
        environment.setSharedSecret(plaintextSecret);
        
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.of(environment));
        
        // When
        String retrievedSecret = backend.retrieve(testEnvironmentId);
        
        // Then
        assertThat(retrievedSecret).isEqualTo(plaintextSecret);
        verify(environmentRepository).findById(testEnvironmentId);
    }
    
    @Test
    void retrieve_shouldThrowSecretNotFoundExceptionWhenEnvironmentNotFound() {
        // Given
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> backend.retrieve(testEnvironmentId))
            .isInstanceOf(SecretNotFoundException.class)
            .hasMessageContaining(testEnvironmentId.toString());
        
        verify(environmentRepository).findById(testEnvironmentId);
    }
    
    @Test
    void retrieve_shouldSplitVersionedValueCorrectly() {
        // Given
        String originalSecret = "test-secret-with-colon:value";
        Environment environment = new Environment();
        environment.setId(testEnvironmentId);
        
        // Store the secret
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.save(any(Environment.class))).thenReturn(environment);
        backend.store(testEnvironmentId, originalSecret);
        
        // Retrieve it
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.of(environment));
        
        // When
        String retrievedSecret = backend.retrieve(testEnvironmentId);
        
        // Then
        assertThat(retrievedSecret).isEqualTo(originalSecret);
    }
    
    @Test
    void retrieve_shouldHandleEmptySecret() {
        // Given
        String originalSecret = "";
        Environment environment = new Environment();
        environment.setId(testEnvironmentId);
        
        // Store the secret
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.save(any(Environment.class))).thenReturn(environment);
        backend.store(testEnvironmentId, originalSecret);
        
        // Retrieve it
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.of(environment));
        
        // When
        String retrievedSecret = backend.retrieve(testEnvironmentId);
        
        // Then
        assertThat(retrievedSecret).isEqualTo(originalSecret);
    }
    
    @Test
    void retrieve_shouldHandleSpecialCharacters() {
        // Given
        String originalSecret = "!@#$%^&*()_+-=[]{}|;':\",./<>?`~";
        Environment environment = new Environment();
        environment.setId(testEnvironmentId);
        
        // Store the secret
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.save(any(Environment.class))).thenReturn(environment);
        backend.store(testEnvironmentId, originalSecret);
        
        // Retrieve it
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.of(environment));
        
        // When
        String retrievedSecret = backend.retrieve(testEnvironmentId);
        
        // Then
        assertThat(retrievedSecret).isEqualTo(originalSecret);
    }
    
    @Test
    void retrieve_shouldHandleUnicodeCharacters() {
        // Given
        String originalSecret = "Hello 世界 🌍 Привет";
        Environment environment = new Environment();
        environment.setId(testEnvironmentId);
        
        // Store the secret
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.save(any(Environment.class))).thenReturn(environment);
        backend.store(testEnvironmentId, originalSecret);
        
        // Retrieve it
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.of(environment));
        
        // When
        String retrievedSecret = backend.retrieve(testEnvironmentId);
        
        // Then
        assertThat(retrievedSecret).isEqualTo(originalSecret);
    }
    
    @Test
    void retrieve_shouldHandleLongSecrets() {
        // Given
        String originalSecret = "a".repeat(1000);
        Environment environment = new Environment();
        environment.setId(testEnvironmentId);
        
        // Store the secret
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.save(any(Environment.class))).thenReturn(environment);
        backend.store(testEnvironmentId, originalSecret);
        
        // Retrieve it
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.of(environment));
        
        // When
        String retrievedSecret = backend.retrieve(testEnvironmentId);
        
        // Then
        assertThat(retrievedSecret).isEqualTo(originalSecret);
    }
    
    @Test
    void retrieve_shouldThrowEncryptionExceptionForInvalidKeyVersion() {
        // Given - environment with encrypted secret using unknown key version
        Environment environment = new Environment();
        environment.setId(testEnvironmentId);
        environment.setSharedSecret("20991231:someencrypteddata");
        
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.of(environment));
        
        // When/Then
        assertThatThrownBy(() -> backend.retrieve(testEnvironmentId))
            .isInstanceOf(EncryptionException.class)
            .hasMessageContaining("Failed to retrieve secret");
    }
    
    @Test
    void retrieve_shouldWrapDecryptionExceptionsInEncryptionException() {
        // Given - environment with invalid encrypted data
        Environment environment = new Environment();
        environment.setId(testEnvironmentId);
        environment.setSharedSecret(KEY_VERSION + ":invalid-base64-data!!!");
        
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.of(environment));
        
        // When/Then
        assertThatThrownBy(() -> backend.retrieve(testEnvironmentId))
            .isInstanceOf(EncryptionException.class)
            .hasMessageContaining("Failed to retrieve secret");
    }
}
