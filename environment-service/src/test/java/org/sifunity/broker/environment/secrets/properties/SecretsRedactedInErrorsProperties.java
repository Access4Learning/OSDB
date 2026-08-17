// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.properties;

import net.jqwik.api.*;
import org.springframework.http.ResponseEntity;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
import org.sifunity.broker.environment.controller.GlobalExceptionHandler;
import org.sifunity.broker.environment.secrets.BackendUnavailableException;
import org.sifunity.broker.environment.secrets.ConfigurationException;
import org.sifunity.broker.environment.secrets.EncryptionException;
import org.sifunity.broker.environment.secrets.SecretNotFoundException;
import org.sifunity.broker.environment.secrets.SecretsBackendException;

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for secrets redacted in error messages.
 * 
 * <p>These tests validate that secrets, encryption keys, and authentication tokens
 * are never exposed in error responses returned by the GlobalExceptionHandler.
 * The SecretRedactor should identify and redact all sensitive patterns before
 * they are included in API error responses.</p>
 * 
 * <p><strong>Validates: Requirements 9.6</strong></p>
 * 
 * <p><strong>Property 16: Secrets Redacted in Error Messages</strong></p>
 * <p>For any exception containing sensitive data (secrets, keys, tokens) in its message,
 * the error response must redact the sensitive data.</p>
 */
@Tag("pluggable-secrets-backend")
class SecretsRedactedInErrorsProperties {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // ========== Property 16: Secrets Redacted in Error Messages ==========

    /**
     * Property 16.1: Secrets in SecretsBackendException are redacted
     * 
     * <p>For any SecretsBackendException containing a secret in its message,
     * the error response description should contain "[REDACTED]" instead of
     * the actual secret value.</p>
     * 
     * <p><strong>Validates: Requirements 9.6</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-16-Secrets-Redacted-In-Errors")
    void secretsInSecretsBackendExceptionAreRedacted(
            @ForAll("secrets") String secret) {
        
        // Given: An exception with a secret in its message
        SecretsBackendException exception = new SecretsBackendException(
            "Failed to process secret: " + secret
        );
        
        // When: The exception is handled by GlobalExceptionHandler
        ResponseEntity<SifErrorResponseEnvelope> response = handler.handleSecretsBackendException(exception);
        
        // Then: The error response should not contain the actual secret
        SifErrorResponse errorResponse = response.getBody().error();
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.description())
            .as("Error response should not contain the actual secret")
            .doesNotContain(secret);
        
        // And: The error response should contain the redacted placeholder
        if (!secret.isEmpty()) {
            assertThat(errorResponse.description())
                .as("Error response should contain [REDACTED] placeholder")
                .contains("[REDACTED]");
        }
    }

    /**
     * Property 16.2: Encryption keys in EncryptionException are redacted
     * 
     * <p>For any EncryptionException containing an encryption key in its message,
     * the error response description should contain "[REDACTED]" instead of
     * the actual key value.</p>
     * 
     * <p><strong>Validates: Requirements 9.6</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-16-Secrets-Redacted-In-Errors")
    void encryptionKeysInEncryptionExceptionAreRedacted(
            @ForAll("encryptionKeys") String encryptionKey) {
        
        // Given: An exception with an encryption key in its message
        EncryptionException exception = new EncryptionException(
            "Decryption failed with key: " + encryptionKey
        );
        
        // When: The exception is handled by GlobalExceptionHandler
        ResponseEntity<SifErrorResponseEnvelope> response = handler.handleEncryptionException(exception);
        
        // Then: The error response should not contain the actual key
        SifErrorResponse errorResponse = response.getBody().error();
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.description())
            .as("Error response should not contain the actual encryption key")
            .doesNotContain(encryptionKey);
        
        // And: The error response should contain the redacted placeholder
        assertThat(errorResponse.description())
            .as("Error response should contain [REDACTED] placeholder")
            .contains("[REDACTED]");
    }

    /**
     * Property 16.3: Tokens in ConfigurationException are redacted
     * 
     * <p>For any ConfigurationException containing a token in its message,
     * the error response description should contain "[REDACTED]" instead of
     * the actual token value.</p>
     * 
     * <p><strong>Validates: Requirements 9.6</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-16-Secrets-Redacted-In-Errors")
    void tokensInConfigurationExceptionAreRedacted(
            @ForAll("vaultTokens") String vaultToken) {
        
        // Given: An exception with a Vault token in its message
        ConfigurationException exception = new ConfigurationException(
            "Invalid Vault token: " + vaultToken
        );
        
        // When: The exception is handled by GlobalExceptionHandler
        ResponseEntity<SifErrorResponseEnvelope> response = handler.handleConfigurationException(exception);
        
        // Then: The error response should not contain the actual token
        SifErrorResponse errorResponse = response.getBody().error();
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.description())
            .as("Error response should not contain the actual Vault token")
            .doesNotContain(vaultToken);
        
        // And: The error response should contain the redacted placeholder
        assertThat(errorResponse.description())
            .as("Error response should contain [REDACTED] placeholder")
            .contains("[REDACTED]");
    }

    /**
     * Property 16.4: Base64-encoded secrets in BackendUnavailableException are redacted
     * 
     * <p>For any BackendUnavailableException containing a base64-encoded secret
     * in its message, the error response description should contain "[REDACTED]"
     * instead of the actual encoded value.</p>
     * 
     * <p><strong>Validates: Requirements 9.6</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-16-Secrets-Redacted-In-Errors")
    void base64SecretsInBackendUnavailableExceptionAreRedacted(
            @ForAll("base64Secrets") String base64Secret) {
        
        // Given: An exception with a base64-encoded secret in its message
        BackendUnavailableException exception = new BackendUnavailableException(
            "Backend unavailable while processing: " + base64Secret
        );
        
        // When: The exception is handled by GlobalExceptionHandler
        ResponseEntity<SifErrorResponseEnvelope> response = handler.handleBackendUnavailableException(exception);
        
        // Then: The error response should not contain the actual base64 value
        SifErrorResponse errorResponse = response.getBody().error();
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.description())
            .as("Error response should not contain the actual base64-encoded secret")
            .doesNotContain(base64Secret);
        
        // And: The error response should contain the redacted placeholder
        assertThat(errorResponse.description())
            .as("Error response should contain [REDACTED] placeholder")
            .contains("[REDACTED]");
    }

    /**
     * Property 16.5: UUIDs in SecretNotFoundException are redacted
     * 
     * <p>For any SecretNotFoundException, the error response description should
     * redact the environment UUID to prevent information disclosure.</p>
     * 
     * <p><strong>Validates: Requirements 9.6</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-16-Secrets-Redacted-In-Errors")
    void uuidsInSecretNotFoundExceptionAreRedacted(
            @ForAll("uuids") UUID environmentId) {
        
        // Given: An exception with a UUID in its message
        SecretNotFoundException exception = new SecretNotFoundException(environmentId);
        
        // When: The exception is handled by GlobalExceptionHandler
        ResponseEntity<SifErrorResponseEnvelope> response = handler.handleSecretNotFoundException(exception);
        
        // Then: The error response should not contain the actual UUID
        SifErrorResponse errorResponse = response.getBody().error();
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.description())
            .as("Error response should not contain the actual UUID")
            .doesNotContain(environmentId.toString());
        
        // And: The error response should contain the redacted placeholder
        assertThat(errorResponse.description())
            .as("Error response should contain [REDACTED] placeholder")
            .contains("[REDACTED]");
    }

    /**
     * Property 16.6: Multiple sensitive values in same exception are all redacted
     * 
     * <p>For any exception containing multiple sensitive values (secret, key, token),
     * all sensitive values should be redacted in the error response.</p>
     * 
     * <p><strong>Validates: Requirements 9.6</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-16-Secrets-Redacted-In-Errors")
    void multipleSensitiveValuesInExceptionAllRedacted(
            @ForAll("secrets") String secret,
            @ForAll("encryptionKeys") String key,
            @ForAll("vaultTokens") String token) {
        
        // Given: An exception with multiple sensitive values in its message
        SecretsBackendException exception = new SecretsBackendException(
            "Operation failed: secret=" + secret + ", key=" + key + ", token=" + token
        );
        
        // When: The exception is handled by GlobalExceptionHandler
        ResponseEntity<SifErrorResponseEnvelope> response = handler.handleSecretsBackendException(exception);
        
        // Then: The error response should not contain any of the sensitive values
        SifErrorResponse errorResponse = response.getBody().error();
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.description())
            .as("Error response should not contain the actual secret")
            .doesNotContain(secret);
        assertThat(errorResponse.description())
            .as("Error response should not contain the actual key")
            .doesNotContain(key);
        assertThat(errorResponse.description())
            .as("Error response should not contain the actual token")
            .doesNotContain(token);
        
        // And: The error response should contain redacted placeholders
        assertThat(errorResponse.description())
            .as("Error response should contain [REDACTED] placeholders")
            .contains("[REDACTED]");
    }

    /**
     * Property 16.7: Secrets in key-value pairs within exceptions are redacted
     * 
     * <p>For any exception containing secrets in key-value format (e.g., "secret: value"),
     * the error response should redact the value portion.</p>
     * 
     * <p><strong>Validates: Requirements 9.6</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-16-Secrets-Redacted-In-Errors")
    void secretsInKeyValuePairsWithinExceptionsAreRedacted(
            @ForAll("secrets") String secret) {
        
        // Given: An exception with secret in key-value format
        EncryptionException exception = new EncryptionException(
            "Configuration error: {secret: " + secret + ", enabled: true}"
        );
        
        // When: The exception is handled by GlobalExceptionHandler
        ResponseEntity<SifErrorResponseEnvelope> response = handler.handleEncryptionException(exception);
        
        // Then: The error response should not contain the actual secret value
        SifErrorResponse errorResponse = response.getBody().error();
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.description())
            .as("Error response should not contain the actual secret value")
            .doesNotContain(secret);
        
        // And: The error response should contain the redacted placeholder
        if (!secret.isEmpty()) {
            assertThat(errorResponse.description())
                .as("Error response should contain [REDACTED] placeholder")
                .contains("[REDACTED]");
        }
    }

    /**
     * Property 16.8: Generic exceptions with secrets are redacted
     * 
     * <p>For any generic Exception containing sensitive data, the error response
     * should redact the sensitive data. This ensures that even unexpected exceptions
     * don't leak secrets.</p>
     * 
     * <p><strong>Validates: Requirements 9.6</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-16-Secrets-Redacted-In-Errors")
    void genericExceptionsWithSecretsAreRedacted(
            @ForAll("secrets") String secret) {
        
        // Given: A generic exception with a secret in its message
        Exception exception = new RuntimeException(
            "Unexpected error with secret: " + secret
        );
        
        // When: The exception is handled by GlobalExceptionHandler
        ResponseEntity<SifErrorResponseEnvelope> response = handler.handleGenericException(exception);
        
        // Then: The error response should have null description (generic errors don't expose details)
        // This is a security feature - generic exceptions don't expose any message details
        SifErrorResponse errorResponse = response.getBody().error();
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.description())
            .as("Generic error response should have null description for security")
            .isNull();
    }

    // ========== Arbitraries (Test Data Generators) ==========

    /**
     * Provides arbitrary secret strings for testing.
     * 
     * <p>Generates strings with alphanumeric characters and common symbols,
     * with lengths between 8 and 64 characters.</p>
     */
    @Provide
    Arbitrary<String> secrets() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars("!@#$%^&*()_+-=")
            .ofMinLength(8)
            .ofMaxLength(64);
    }

    /**
     * Provides arbitrary encryption keys for testing.
     * 
     * <p>Generates hexadecimal strings representing 256-bit encryption keys
     * (64 hex characters = 32 bytes = 256 bits).</p>
     */
    @Provide
    Arbitrary<String> encryptionKeys() {
        return Arbitraries.strings()
            .withCharRange('0', '9')
            .withCharRange('a', 'f')
            .ofLength(64);
    }

    /**
     * Provides arbitrary Vault tokens for testing.
     * 
     * <p>Generates JWT-like tokens with three base64-encoded segments
     * separated by dots.</p>
     */
    @Provide
    Arbitrary<String> vaultTokens() {
        return Combinators.combine(
            base64Segment(),
            base64Segment(),
            base64Segment()
        ).as((header, payload, signature) -> 
            "eyJ" + header + "." + payload + "." + signature
        );
    }

    /**
     * Provides arbitrary base64-encoded secrets for testing.
     * 
     * <p>Generates base64-encoded strings of at least 20 characters
     * to match the SecretRedactor pattern.</p>
     */
    @Provide
    Arbitrary<String> base64Secrets() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .ofMinLength(15)
            .ofMaxLength(50)
            .map(s -> Base64.getEncoder().encodeToString(s.getBytes()));
    }

    /**
     * Provides arbitrary UUIDs for testing.
     * 
     * <p>Generates random UUIDs that should be redacted from error messages.</p>
     */
    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }

    /**
     * Helper method to generate base64-like segments for JWT tokens.
     */
    private Arbitrary<String> base64Segment() {
        return Arbitraries.strings()
            .withCharRange('A', 'Z')
            .withCharRange('a', 'z')
            .withCharRange('0', '9')
            .withChars("-_")
            .ofMinLength(10)
            .ofMaxLength(30);
    }
}
