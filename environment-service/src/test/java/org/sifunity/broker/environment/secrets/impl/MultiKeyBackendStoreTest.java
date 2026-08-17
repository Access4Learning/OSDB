// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.secrets.SecretNotFoundException;
import org.sifunity.broker.environment.secrets.SecretsBackendException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the store method in MultiKeyBackend.
 * 
 * <p>These tests verify that the store method correctly:
 * <ul>
 *   <li>Encrypts secrets using the current key version</li>
 *   <li>Prefixes encrypted values with "YYYYMMDD:" format</li>
 *   <li>Loads Environment entity from repository</li>
 *   <li>Sets sharedSecret field with versioned value</li>
 *   <li>Saves entity to repository</li>
 *   <li>Wraps exceptions in SecretsBackendException</li>
 * </ul>
 * 
 * <p><b>Validates Requirements:</b> 2.3, 2.4, 2.8
 */
@ExtendWith(MockitoExtension.class)
class MultiKeyBackendStoreTest {
    
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
    void store_shouldEncryptAndSaveSecret() {
        // Given
        String secret = "my-secret-password-123";
        Environment environment = new Environment();
        environment.setId(testEnvironmentId);
        
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.save(any(Environment.class))).thenReturn(environment);
        
        // When
        backend.store(testEnvironmentId, secret);
        
        // Then
        verify(environmentRepository).findById(testEnvironmentId);
        verify(environmentRepository).save(environment);
        
        // Verify the sharedSecret was set with version prefix
        String storedValue = environment.getSharedSecret();
        assertThat(storedValue).isNotNull();
        assertThat(storedValue).startsWith(KEY_VERSION + ":");
        assertThat(storedValue.length()).isGreaterThan(KEY_VERSION.length() + 1);
    }
    
    @Test
    void store_shouldPrefixWithVersionInCorrectFormat() {
        // Given
        String secret = "test-secret";
        Environment environment = new Environment();
        environment.setId(testEnvironmentId);
        
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.save(any(Environment.class))).thenReturn(environment);
        
        // When
        backend.store(testEnvironmentId, secret);
        
        // Then
        String storedValue = environment.getSharedSecret();
        assertThat(storedValue).matches("^\\d{8}:.+$"); // YYYYMMDD:encrypted_data format
        assertThat(storedValue).startsWith(KEY_VERSION + ":");
    }
    
    @Test
    void store_shouldThrowSecretNotFoundExceptionWhenEnvironmentNotFound() {
        // Given
        String secret = "test-secret";
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> backend.store(testEnvironmentId, secret))
            .isInstanceOf(SecretNotFoundException.class)
            .hasMessageContaining(testEnvironmentId.toString());
        
        verify(environmentRepository).findById(testEnvironmentId);
        verify(environmentRepository, never()).save(any());
    }
    
    @Test
    void store_shouldWrapRepositoryExceptionsInSecretsBackendException() {
        // Given
        String secret = "test-secret";
        Environment environment = new Environment();
        environment.setId(testEnvironmentId);
        
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.save(any(Environment.class)))
            .thenThrow(new RuntimeException("Database connection failed"));
        
        // When/Then
        assertThatThrownBy(() -> backend.store(testEnvironmentId, secret))
            .isInstanceOf(SecretsBackendException.class)
            .hasMessageContaining("Failed to store encrypted secret")
            .hasMessageContaining(testEnvironmentId.toString())
            .hasCauseInstanceOf(RuntimeException.class);
    }
    
    @Test
    void store_shouldHandleEmptySecret() {
        // Given
        String secret = "";
        Environment environment = new Environment();
        environment.setId(testEnvironmentId);
        
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.save(any(Environment.class))).thenReturn(environment);
        
        // When
        backend.store(testEnvironmentId, secret);
        
        // Then
        verify(environmentRepository).save(environment);
        String storedValue = environment.getSharedSecret();
        assertThat(storedValue).isNotNull();
        assertThat(storedValue).startsWith(KEY_VERSION + ":");
    }
    
    @Test
    void store_shouldHandleSpecialCharacters() {
        // Given
        String secret = "!@#$%^&*()_+-=[]{}|;':\",./<>?`~";
        Environment environment = new Environment();
        environment.setId(testEnvironmentId);
        
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.save(any(Environment.class))).thenReturn(environment);
        
        // When
        backend.store(testEnvironmentId, secret);
        
        // Then
        verify(environmentRepository).save(environment);
        String storedValue = environment.getSharedSecret();
        assertThat(storedValue).isNotNull();
        assertThat(storedValue).startsWith(KEY_VERSION + ":");
    }
    
    @Test
    void store_shouldHandleUnicodeCharacters() {
        // Given
        String secret = "Hello 世界 🌍 Привет";
        Environment environment = new Environment();
        environment.setId(testEnvironmentId);
        
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.save(any(Environment.class))).thenReturn(environment);
        
        // When
        backend.store(testEnvironmentId, secret);
        
        // Then
        verify(environmentRepository).save(environment);
        String storedValue = environment.getSharedSecret();
        assertThat(storedValue).isNotNull();
        assertThat(storedValue).startsWith(KEY_VERSION + ":");
    }
    
    @Test
    void store_shouldHandleLongSecrets() {
        // Given
        String secret = "a".repeat(1000);
        Environment environment = new Environment();
        environment.setId(testEnvironmentId);
        
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.save(any(Environment.class))).thenReturn(environment);
        
        // When
        backend.store(testEnvironmentId, secret);
        
        // Then
        verify(environmentRepository).save(environment);
        String storedValue = environment.getSharedSecret();
        assertThat(storedValue).isNotNull();
        assertThat(storedValue).startsWith(KEY_VERSION + ":");
    }
    
    private int greaterThan(int value) {
        return value + 1;
    }
}
