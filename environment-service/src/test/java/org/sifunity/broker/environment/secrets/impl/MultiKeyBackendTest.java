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
 * Comprehensive unit tests for MultiKeyBackend.
 * 
 * <p>These tests verify the complete functionality of the MultiKeyBackend including:
 * <ul>
 *   <li>Store and retrieve with single key</li>
 *   <li>Retrieve with multiple key versions</li>
 *   <li>Handling of plaintext secrets (backward compatibility)</li>
 *   <li>SecretNotFoundException scenarios</li>
 *   <li>EncryptionException for missing key version</li>
 *   <li>Empty secret handling</li>
 * </ul>
 * 
 * <p><b>Validates Requirements:</b> 11.1
 */
@ExtendWith(MockitoExtension.class)
class MultiKeyBackendTest {
    
    @Mock
    private EnvironmentRepository environmentRepository;
    
    private MultiKeyBackend backend;
    private static final String CURRENT_KEY_VERSION = "20240115";
    private static final String OLD_KEY_VERSION = "20240101";
    private UUID testEnvironmentId;
    private SecretKey currentSecretKey;
    private SecretKey oldSecretKey;
    
    @BeforeEach
    void setUp() throws Exception {
        // Generate valid 256-bit AES keys
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        currentSecretKey = keyGen.generateKey();
        oldSecretKey = keyGen.generateKey();
        
        String currentBase64Key = Base64.getEncoder().encodeToString(currentSecretKey.getEncoded());
        String oldBase64Key = Base64.getEncoder().encodeToString(oldSecretKey.getEncoded());
        
        // Create properties with multiple keys
        MultiKeyProperties properties = new MultiKeyProperties();
        properties.setCurrentVersion(CURRENT_KEY_VERSION);
        Map<String, String> keys = new HashMap<>();
        keys.put(CURRENT_KEY_VERSION, currentBase64Key);
        keys.put(OLD_KEY_VERSION, oldBase64Key);
        properties.setKeys(keys);
        
        // Create backend instance
        backend = new MultiKeyBackend(environmentRepository, properties);
        
        // Manually set the keyVersions map using reflection to bypass loadKeys
        Field keyVersionsField = MultiKeyBackend.class.getDeclaredField("keyVersions");
        keyVersionsField.setAccessible(true);
        Map<String, SecretKey> keyVersionsMap = new HashMap<>();
        keyVersionsMap.put(CURRENT_KEY_VERSION, currentSecretKey);
        keyVersionsMap.put(OLD_KEY_VERSION, oldSecretKey);
        keyVersionsField.set(backend, keyVersionsMap);
        
        testEnvironmentId = UUID.randomUUID();
    }
    
    // ========== Store and Retrieve with Single Key ==========
    
    @Test
    void storeAndRetrieve_shouldWorkWithSingleKey() {
        // Given
        String originalSecret = "my-secret-password-123";
        Environment environment = new Environment();
        environment.setId(testEnvironmentId);
        
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.save(any(Environment.class))).thenReturn(environment);
        
        // When
        backend.store(testEnvironmentId, originalSecret);
        String retrievedSecret = backend.retrieve(testEnvironmentId);
        
        // Then
        assertThat(retrievedSecret).isEqualTo(originalSecret);
        verify(environmentRepository, times(2)).findById(testEnvironmentId);
        verify(environmentRepository).save(environment);
    }
    
    @Test
    void store_shouldPrefixWithCurrentKeyVersion() {
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
        assertThat(storedValue).isNotNull();
        assertThat(storedValue).startsWith(CURRENT_KEY_VERSION + ":");
        assertThat(storedValue).matches("^\\d{8}:.+$"); // YYYYMMDD:encrypted_data format
    }
    
    // ========== Retrieve with Multiple Key Versions ==========
    
    @Test
    void retrieve_shouldDecryptSecretEncryptedWithOldKeyVersion() throws Exception {
        // Given - create a backend with only the old key to encrypt
        MultiKeyProperties oldProperties = new MultiKeyProperties();
        oldProperties.setCurrentVersion(OLD_KEY_VERSION);
        Map<String, String> oldKeys = new HashMap<>();
        oldKeys.put(OLD_KEY_VERSION, Base64.getEncoder().encodeToString(oldSecretKey.getEncoded()));
        oldProperties.setKeys(oldKeys);
        
        MultiKeyBackend oldBackend = new MultiKeyBackend(environmentRepository, oldProperties);
        
        // Set the keyVersions map for old backend
        Field keyVersionsField = MultiKeyBackend.class.getDeclaredField("keyVersions");
        keyVersionsField.setAccessible(true);
        Map<String, SecretKey> oldKeyVersionsMap = new HashMap<>();
        oldKeyVersionsMap.put(OLD_KEY_VERSION, oldSecretKey);
        keyVersionsField.set(oldBackend, oldKeyVersionsMap);
        
        String originalSecret = "secret-encrypted-with-old-key";
        Environment environment = new Environment();
        environment.setId(testEnvironmentId);
        
        // Store with old backend (uses old key)
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.save(any(Environment.class))).thenReturn(environment);
        oldBackend.store(testEnvironmentId, originalSecret);
        
        // Verify it was stored with old key version
        assertThat(environment.getSharedSecret()).startsWith(OLD_KEY_VERSION + ":");
        
        // When - retrieve with new backend (has both keys)
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.of(environment));
        String retrievedSecret = backend.retrieve(testEnvironmentId);
        
        // Then
        assertThat(retrievedSecret).isEqualTo(originalSecret);
    }
    
    @Test
    void retrieve_shouldHandleMultipleKeyVersionsInSameDatabase() throws Exception {
        // Given - two environments with secrets encrypted with different key versions
        UUID env1Id = UUID.randomUUID();
        UUID env2Id = UUID.randomUUID();
        
        String secret1 = "secret-with-current-key";
        String secret2 = "secret-with-old-key";
        
        Environment env1 = new Environment();
        env1.setId(env1Id);
        
        Environment env2 = new Environment();
        env2.setId(env2Id);
        
        // Store first secret with current key
        when(environmentRepository.findById(env1Id)).thenReturn(Optional.of(env1));
        when(environmentRepository.save(any(Environment.class))).thenReturn(env1);
        backend.store(env1Id, secret1);
        
        // Store second secret with old key (simulate old encryption)
        MultiKeyProperties oldProperties = new MultiKeyProperties();
        oldProperties.setCurrentVersion(OLD_KEY_VERSION);
        Map<String, String> oldKeys = new HashMap<>();
        oldKeys.put(OLD_KEY_VERSION, Base64.getEncoder().encodeToString(oldSecretKey.getEncoded()));
        oldProperties.setKeys(oldKeys);
        
        MultiKeyBackend oldBackend = new MultiKeyBackend(environmentRepository, oldProperties);
        Field keyVersionsField = MultiKeyBackend.class.getDeclaredField("keyVersions");
        keyVersionsField.setAccessible(true);
        Map<String, SecretKey> oldKeyVersionsMap = new HashMap<>();
        oldKeyVersionsMap.put(OLD_KEY_VERSION, oldSecretKey);
        keyVersionsField.set(oldBackend, oldKeyVersionsMap);
        
        when(environmentRepository.findById(env2Id)).thenReturn(Optional.of(env2));
        when(environmentRepository.save(any(Environment.class))).thenReturn(env2);
        oldBackend.store(env2Id, secret2);
        
        // When - retrieve both secrets with the multi-key backend
        when(environmentRepository.findById(env1Id)).thenReturn(Optional.of(env1));
        when(environmentRepository.findById(env2Id)).thenReturn(Optional.of(env2));
        
        String retrieved1 = backend.retrieve(env1Id);
        String retrieved2 = backend.retrieve(env2Id);
        
        // Then - both should be decrypted correctly
        assertThat(retrieved1).isEqualTo(secret1);
        assertThat(retrieved2).isEqualTo(secret2);
    }
    
    // ========== Handling of Plaintext Secrets (Backward Compatibility) ==========
    
    @Test
    void retrieve_shouldHandlePlaintextSecretsWithoutVersionPrefix() {
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
    void retrieve_shouldHandlePlaintextSecretsWithColonInValue() {
        // Given - plaintext secret that contains a colon but not in version format
        String plaintextSecret = "plaintext:with:colons";
        Environment environment = new Environment();
        environment.setId(testEnvironmentId);
        environment.setSharedSecret(plaintextSecret);
        
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.of(environment));
        
        // When
        String retrievedSecret = backend.retrieve(testEnvironmentId);
        
        // Then - should return the plaintext value
        assertThat(retrievedSecret).isEqualTo(plaintextSecret);
    }
    
    // ========== SecretNotFoundException ==========
    
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
    void delete_shouldThrowSecretNotFoundExceptionWhenEnvironmentNotFound() {
        // Given
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> backend.delete(testEnvironmentId))
            .isInstanceOf(SecretNotFoundException.class)
            .hasMessageContaining(testEnvironmentId.toString());
        
        verify(environmentRepository).findById(testEnvironmentId);
        verify(environmentRepository, never()).save(any());
    }
    
    // ========== EncryptionException for Missing Key Version ==========
    
    @Test
    void retrieve_shouldThrowEncryptionExceptionForMissingKeyVersion() {
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
    void retrieve_shouldThrowEncryptionExceptionForInvalidEncryptedData() {
        // Given - environment with invalid encrypted data
        Environment environment = new Environment();
        environment.setId(testEnvironmentId);
        environment.setSharedSecret(CURRENT_KEY_VERSION + ":invalid-base64-data!!!");
        
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.of(environment));
        
        // When/Then
        assertThatThrownBy(() -> backend.retrieve(testEnvironmentId))
            .isInstanceOf(EncryptionException.class)
            .hasMessageContaining("Failed to retrieve secret");
    }
    
    @Test
    void retrieve_shouldThrowEncryptionExceptionForTamperedData() throws Exception {
        // Given - store a valid secret
        String originalSecret = "my-secret";
        Environment environment = new Environment();
        environment.setId(testEnvironmentId);
        
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.save(any(Environment.class))).thenReturn(environment);
        backend.store(testEnvironmentId, originalSecret);
        
        // Tamper with the encrypted data
        String storedValue = environment.getSharedSecret();
        String[] parts = storedValue.split(":", 2);
        String version = parts[0];
        String encrypted = parts[1];
        
        byte[] encryptedBytes = Base64.getDecoder().decode(encrypted);
        encryptedBytes[encryptedBytes.length - 1] ^= 1; // Flip one bit in the auth tag
        String tamperedEncrypted = Base64.getEncoder().encodeToString(encryptedBytes);
        
        environment.setSharedSecret(version + ":" + tamperedEncrypted);
        
        // When/Then - decryption should fail due to authentication tag mismatch
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.of(environment));
        assertThatThrownBy(() -> backend.retrieve(testEnvironmentId))
            .isInstanceOf(EncryptionException.class)
            .hasMessageContaining("Failed to retrieve secret");
    }
    
    // ========== Empty Secret Handling ==========
    
    @Test
    void storeAndRetrieve_shouldHandleEmptySecret() {
        // Given
        String emptySecret = "";
        Environment environment = new Environment();
        environment.setId(testEnvironmentId);
        
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.save(any(Environment.class))).thenReturn(environment);
        
        // When
        backend.store(testEnvironmentId, emptySecret);
        String retrievedSecret = backend.retrieve(testEnvironmentId);
        
        // Then
        assertThat(retrievedSecret).isEqualTo(emptySecret);
        assertThat(environment.getSharedSecret()).startsWith(CURRENT_KEY_VERSION + ":");
    }
    
    @Test
    void store_shouldHandleEmptySecretWithCorrectFormat() {
        // Given
        String emptySecret = "";
        Environment environment = new Environment();
        environment.setId(testEnvironmentId);
        
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.save(any(Environment.class))).thenReturn(environment);
        
        // When
        backend.store(testEnvironmentId, emptySecret);
        
        // Then
        String storedValue = environment.getSharedSecret();
        assertThat(storedValue).isNotNull();
        assertThat(storedValue).startsWith(CURRENT_KEY_VERSION + ":");
        assertThat(storedValue).matches("^\\d{8}:.+$");
    }
    
    // ========== Additional Edge Cases ==========
    
    @Test
    void storeAndRetrieve_shouldHandleSpecialCharacters() {
        // Given
        String secretWithSpecialChars = "!@#$%^&*()_+-=[]{}|;':\",./<>?`~";
        Environment environment = new Environment();
        environment.setId(testEnvironmentId);
        
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.save(any(Environment.class))).thenReturn(environment);
        
        // When
        backend.store(testEnvironmentId, secretWithSpecialChars);
        String retrievedSecret = backend.retrieve(testEnvironmentId);
        
        // Then
        assertThat(retrievedSecret).isEqualTo(secretWithSpecialChars);
    }
    
    @Test
    void storeAndRetrieve_shouldHandleUnicodeCharacters() {
        // Given
        String secretWithUnicode = "Hello 世界 🌍 Привет";
        Environment environment = new Environment();
        environment.setId(testEnvironmentId);
        
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.save(any(Environment.class))).thenReturn(environment);
        
        // When
        backend.store(testEnvironmentId, secretWithUnicode);
        String retrievedSecret = backend.retrieve(testEnvironmentId);
        
        // Then
        assertThat(retrievedSecret).isEqualTo(secretWithUnicode);
    }
    
    @Test
    void storeAndRetrieve_shouldHandleLongSecrets() {
        // Given
        String longSecret = "a".repeat(1000);
        Environment environment = new Environment();
        environment.setId(testEnvironmentId);
        
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.save(any(Environment.class))).thenReturn(environment);
        
        // When
        backend.store(testEnvironmentId, longSecret);
        String retrievedSecret = backend.retrieve(testEnvironmentId);
        
        // Then
        assertThat(retrievedSecret).isEqualTo(longSecret);
    }
    
    @Test
    void storeAndRetrieve_shouldHandleSecretsWithColons() {
        // Given
        String secretWithColons = "secret:with:multiple:colons:in:value";
        Environment environment = new Environment();
        environment.setId(testEnvironmentId);
        
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.save(any(Environment.class))).thenReturn(environment);
        
        // When
        backend.store(testEnvironmentId, secretWithColons);
        String retrievedSecret = backend.retrieve(testEnvironmentId);
        
        // Then
        assertThat(retrievedSecret).isEqualTo(secretWithColons);
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
    void delete_shouldSetSharedSecretToNull() {
        // Given
        Environment environment = new Environment();
        environment.setId(testEnvironmentId);
        environment.setSharedSecret("some-existing-secret");
        
        when(environmentRepository.findById(testEnvironmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.save(any(Environment.class))).thenReturn(environment);
        
        // When
        backend.delete(testEnvironmentId);
        
        // Then
        assertThat(environment.getSharedSecret()).isNull();
        verify(environmentRepository).save(environment);
    }
    
    @Test
    void isHealthy_shouldReturnTrueWhenConfigurationValid() {
        // When
        boolean healthy = backend.isHealthy();
        
        // Then
        assertThat(healthy).isTrue();
    }
    
    @Test
    void isHealthy_shouldReturnFalseWhenCurrentKeyVersionMissing() throws Exception {
        // Given - create backend with missing current key version
        Field currentKeyVersionField = MultiKeyBackend.class.getDeclaredField("currentKeyVersion");
        currentKeyVersionField.setAccessible(true);
        currentKeyVersionField.set(backend, "20991231"); // Non-existent version
        
        // When
        boolean healthy = backend.isHealthy();
        
        // Then
        assertThat(healthy).isFalse();
    }
    
    @Test
    void getBackendType_shouldReturnMultiKey() {
        // When
        String backendType = backend.getBackendType();
        
        // Then
        assertThat(backendType).isEqualTo("multi-key");
    }
}
