// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
import org.sifunity.broker.environment.secrets.BackendUnavailableException;
import org.sifunity.broker.environment.secrets.ConfigurationException;
import org.sifunity.broker.environment.secrets.EncryptionException;
import org.sifunity.broker.environment.secrets.SecretNotFoundException;
import org.sifunity.broker.environment.secrets.SecretsBackendException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for GlobalExceptionHandler.
 * 
 * <p>Verifies that exception handlers properly redact sensitive data from error responses
 * and return appropriate HTTP status codes.</p>
 * 
 * <p>Requirements: 9.3, 9.4, 9.7</p>
 */
class GlobalExceptionHandlerTest {
    
    private GlobalExceptionHandler handler;
    
    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }
    
    @Test
    void handleSecretNotFoundException_shouldRedactUUIDs() {
        // Given
        UUID environmentId = UUID.randomUUID();
        SecretNotFoundException ex = new SecretNotFoundException(environmentId);
        
        // When
        ResponseEntity<SifErrorResponseEnvelope> response = handler.handleSecretNotFoundException(ex);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().subCode()).isEqualTo("SECRET_001");
        assertThat(response.getBody().error().message()).isEqualTo("Secret not found");
        
        // Verify UUID is redacted
        String description = response.getBody().error().description();
        assertThat(description).doesNotContain(environmentId.toString());
        assertThat(description).contains("[REDACTED]");
    }
    
    @Test
    void handleBackendUnavailableException_shouldRedactSecrets() {
        // Given
        String secretValue = "mySecretValue123";
        BackendUnavailableException ex = new BackendUnavailableException(
            "Vault unavailable, failed to store secret: " + secretValue
        );
        
        // When
        ResponseEntity<SifErrorResponseEnvelope> response = handler.handleBackendUnavailableException(ex);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().subCode()).isEqualTo("SECRET_002");
        assertThat(response.getBody().error().message()).isEqualTo("Secrets backend unavailable");
        
        // Verify secret is redacted
        String description = response.getBody().error().description();
        assertThat(description).doesNotContain(secretValue);
    }
    
    @Test
    void handleEncryptionException_shouldRedactBase64Data() {
        // Given
        String base64Secret = "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY3ODkw"; // Long base64 string
        EncryptionException ex = new EncryptionException(
            "Failed to decrypt: " + base64Secret
        );
        
        // When
        ResponseEntity<SifErrorResponseEnvelope> response = handler.handleEncryptionException(ex);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().subCode()).isEqualTo("SECRET_003");
        assertThat(response.getBody().error().message()).isEqualTo("Encryption operation failed");
        
        // Verify base64 data is redacted
        String description = response.getBody().error().description();
        assertThat(description).doesNotContain(base64Secret);
        assertThat(description).contains("[REDACTED]");
    }
    
    @Test
    void handleConfigurationException_shouldRedactKeys() {
        // Given
        String encryptionKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        ConfigurationException ex = new ConfigurationException(
            "Invalid encryption key: " + encryptionKey
        );
        
        // When
        ResponseEntity<SifErrorResponseEnvelope> response = handler.handleConfigurationException(ex);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().subCode()).isEqualTo("SECRET_004");
        assertThat(response.getBody().error().message()).isEqualTo("Secrets backend configuration error");
        
        // Verify encryption key is redacted
        String description = response.getBody().error().description();
        assertThat(description).doesNotContain(encryptionKey);
        assertThat(description).contains("[REDACTED]");
    }
    
    @Test
    void handleSecretsBackendException_shouldRedactSensitiveData() {
        // Given
        String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U";
        SecretsBackendException ex = new SecretsBackendException(
            "Backend operation failed with token: " + token
        );
        
        // When
        ResponseEntity<SifErrorResponseEnvelope> response = handler.handleSecretsBackendException(ex);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().subCode()).isEqualTo("SECRET_005");
        assertThat(response.getBody().error().message()).isEqualTo("Secrets backend error");
        
        // Verify JWT token is redacted
        String description = response.getBody().error().description();
        assertThat(description).doesNotContain(token);
        assertThat(description).contains("[REDACTED]");
    }
    
    @Test
    void handleGenericException_shouldRedactSecretsInMessage() {
        // Given
        String secret = "password=mySecretPassword123";
        Exception ex = new RuntimeException("Operation failed: " + secret);
        
        // When
        ResponseEntity<SifErrorResponseEnvelope> response = handler.handleGenericException(ex);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().subCode()).isEqualTo("INTERNAL_001");
        assertThat(response.getBody().error().message()).isEqualTo("Internal server error");
        
        // Description should be null for generic exceptions (security measure)
        assertThat(response.getBody().error().description()).isNull();
    }
    
    @Test
    void handleSecretNotFoundException_withNullMessage_shouldNotFail() {
        // Given
        UUID environmentId = UUID.randomUUID();
        SecretNotFoundException ex = new SecretNotFoundException(environmentId);
        
        // When
        ResponseEntity<SifErrorResponseEnvelope> response = handler.handleSecretNotFoundException(ex);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().subCode()).isEqualTo("SECRET_001");
    }
    
    @Test
    void handleEncryptionException_withKeyValuePair_shouldRedactValue() {
        // Given
        EncryptionException ex = new EncryptionException(
            "Encryption failed: key=abc123xyz456def789"
        );
        
        // When
        ResponseEntity<SifErrorResponseEnvelope> response = handler.handleEncryptionException(ex);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        
        // Verify key-value pair is redacted
        String description = response.getBody().error().description();
        assertThat(description).contains("[REDACTED]");
        assertThat(description).doesNotContain("abc123xyz456def789");
    }
}
