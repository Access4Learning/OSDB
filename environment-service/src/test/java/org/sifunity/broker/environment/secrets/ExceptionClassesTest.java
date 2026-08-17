// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for secrets backend exception classes.
 * 
 * <p>These tests verify that all exception classes are properly constructed,
 * maintain correct inheritance relationships, and preserve exception details
 * such as messages, causes, and custom fields.
 */
class ExceptionClassesTest {
    
    @Test
    void secretsBackendException_withMessage_shouldCreateException() {
        // Given
        String message = "Backend operation failed";
        
        // When
        SecretsBackendException exception = new SecretsBackendException(message);
        
        // Then
        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isNull();
    }
    
    @Test
    void secretsBackendException_withMessageAndCause_shouldCreateException() {
        // Given
        String message = "Backend operation failed";
        Throwable cause = new IllegalStateException("Invalid state");
        
        // When
        SecretsBackendException exception = new SecretsBackendException(message, cause);
        
        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }
    
    @Test
    void secretNotFoundException_withEnvironmentId_shouldCreateException() {
        // Given
        UUID environmentId = UUID.randomUUID();
        
        // When
        SecretNotFoundException exception = new SecretNotFoundException(environmentId);
        
        // Then
        assertThat(exception).isInstanceOf(SecretsBackendException.class);
        assertThat(exception.getMessage()).contains(environmentId.toString());
        assertThat(exception.getEnvironmentId()).isEqualTo(environmentId);
    }
    
    @Test
    void secretNotFoundException_withNullEnvironmentId_shouldThrowException() {
        // When/Then
        assertThatThrownBy(() -> new SecretNotFoundException(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("environmentId must not be null");
    }
    
    @Test
    void secretNotFoundException_shouldInheritFromSecretsBackendException() {
        // Given
        UUID environmentId = UUID.randomUUID();
        
        // When
        SecretNotFoundException exception = new SecretNotFoundException(environmentId);
        
        // Then
        assertThat(exception).isInstanceOf(SecretsBackendException.class);
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }
    
    @Test
    void backendUnavailableException_withMessage_shouldCreateException() {
        // Given
        String message = "Vault server unreachable";
        
        // When
        BackendUnavailableException exception = new BackendUnavailableException(message);
        
        // Then
        assertThat(exception).isInstanceOf(SecretsBackendException.class);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isNull();
    }
    
    @Test
    void backendUnavailableException_withMessageAndCause_shouldCreateException() {
        // Given
        String message = "Failed to connect to Vault";
        Throwable cause = new java.net.ConnectException("Connection refused");
        
        // When
        BackendUnavailableException exception = new BackendUnavailableException(message, cause);
        
        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }
    
    @Test
    void encryptionException_withMessage_shouldCreateException() {
        // Given
        String message = "Decryption failed: authentication tag mismatch";
        
        // When
        EncryptionException exception = new EncryptionException(message);
        
        // Then
        assertThat(exception).isInstanceOf(SecretsBackendException.class);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isNull();
    }
    
    @Test
    void encryptionException_withMessageAndCause_shouldCreateException() {
        // Given
        String message = "Failed to decrypt secret";
        Throwable cause = new javax.crypto.AEADBadTagException("Tag mismatch");
        
        // When
        EncryptionException exception = new EncryptionException(message, cause);
        
        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }
    
    @Test
    void configurationException_withMessage_shouldCreateException() {
        // Given
        String message = "No encryption keys configured for MultiKeyBackend";
        
        // When
        ConfigurationException exception = new ConfigurationException(message);
        
        // Then
        assertThat(exception).isInstanceOf(SecretsBackendException.class);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isNull();
    }
    
    @Test
    void configurationException_withMessageAndCause_shouldCreateException() {
        // Given
        String message = "Encryption key is not valid base64";
        Throwable cause = new IllegalArgumentException("Invalid base64");
        
        // When
        ConfigurationException exception = new ConfigurationException(message, cause);
        
        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }
    
    @Test
    void allExceptions_shouldBeRuntimeExceptions() {
        // Given
        UUID environmentId = UUID.randomUUID();
        
        // When/Then
        assertThat(new SecretsBackendException("test")).isInstanceOf(RuntimeException.class);
        assertThat(new SecretNotFoundException(environmentId)).isInstanceOf(RuntimeException.class);
        assertThat(new BackendUnavailableException("test")).isInstanceOf(RuntimeException.class);
        assertThat(new EncryptionException("test")).isInstanceOf(RuntimeException.class);
        assertThat(new ConfigurationException("test")).isInstanceOf(RuntimeException.class);
    }
    
    @Test
    void exceptionHierarchy_shouldBeCorrect() {
        // Given
        UUID environmentId = UUID.randomUUID();
        
        // When
        SecretNotFoundException secretNotFound = new SecretNotFoundException(environmentId);
        BackendUnavailableException backendUnavailable = new BackendUnavailableException("test");
        EncryptionException encryption = new EncryptionException("test");
        ConfigurationException configuration = new ConfigurationException("test");
        
        // Then - all should inherit from SecretsBackendException
        assertThat(secretNotFound).isInstanceOf(SecretsBackendException.class);
        assertThat(backendUnavailable).isInstanceOf(SecretsBackendException.class);
        assertThat(encryption).isInstanceOf(SecretsBackendException.class);
        assertThat(configuration).isInstanceOf(SecretsBackendException.class);
    }
    
    @Test
    void secretNotFoundException_messageFormat_shouldIncludeEnvironmentId() {
        // Given
        UUID environmentId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        
        // When
        SecretNotFoundException exception = new SecretNotFoundException(environmentId);
        
        // Then
        assertThat(exception.getMessage())
            .isEqualTo("Secret not found for environment: 123e4567-e89b-12d3-a456-426614174000");
    }
}
