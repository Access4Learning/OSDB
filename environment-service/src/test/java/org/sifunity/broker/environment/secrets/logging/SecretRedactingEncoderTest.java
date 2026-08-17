// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.environment.secrets.util.SecretRedactor;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SecretRedactingEncoder}.
 * 
 * <p>Verifies that the encoder correctly redacts sensitive data from log output
 * including secrets, encryption keys, tokens, and other sensitive patterns.
 * 
 * <p>Note: These tests verify the SecretRedactor integration. The actual encoding
 * behavior is tested through integration tests with a real Logback configuration.
 */
class SecretRedactingEncoderTest {
    
    private SecretRedactingEncoder encoder;
    private LoggerContext loggerContext;
    private Logger logger;
    
    @BeforeEach
    void setUp() {
        loggerContext = new LoggerContext();
        logger = loggerContext.getLogger("test");
        
        encoder = new SecretRedactingEncoder();
        encoder.setContext(loggerContext);
        encoder.setPattern("%msg%n");
        encoder.start();
    }
    
    @Test
    void shouldRedactSecretsUsingSecretRedactor() {
        // Given - test that SecretRedactor is being used
        String messageWithSecret = "Encrypted secret: YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY=";
        
        // When - apply redaction directly
        String redacted = SecretRedactor.redactSecrets(messageWithSecret);
        
        // Then - verify redaction works
        assertThat(redacted).contains("[REDACTED]");
        assertThat(redacted).doesNotContain("YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY=");
    }
    
    @Test
    void shouldRedactEncryptionKeys() {
        // Given
        String message = "Using key: 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        
        // When
        String redacted = SecretRedactor.redactSecrets(message);
        
        // Then
        assertThat(redacted).contains("[REDACTED]");
        assertThat(redacted).doesNotContain("0123456789abcdef");
    }
    
    @Test
    void shouldRedactTokens() {
        // Given
        String message = "Authentication token: hvs.CAESIJ1234567890abcdefghijklmnop";
        
        // When
        String redacted = SecretRedactor.redactSecrets(message);
        
        // Then
        assertThat(redacted).contains("[REDACTED]");
        assertThat(redacted).doesNotContain("hvs.CAESIJ1234567890abcdefghijklmnop");
    }
    
    @Test
    void shouldRedactJWTTokens() {
        // Given
        String message = "JWT: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U";
        
        // When
        String redacted = SecretRedactor.redactSecrets(message);
        
        // Then
        assertThat(redacted).contains("[REDACTED]");
        assertThat(redacted).doesNotContain("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9");
    }
    
    @Test
    void shouldRedactSensitiveFieldValues() {
        // Given
        String message = "Config: {secret: mySecretValue123, password: admin123}";
        
        // When
        String redacted = SecretRedactor.redactSecrets(message);
        
        // Then
        assertThat(redacted).contains("[REDACTED]");
        assertThat(redacted).doesNotContain("mySecretValue123");
        assertThat(redacted).doesNotContain("admin123");
    }
    
    @Test
    void shouldRedactUUIDs() {
        // Given
        String message = "Secret ID: 550e8400-e29b-41d4-a716-446655440000";
        
        // When
        String redacted = SecretRedactor.redactSecrets(message);
        
        // Then
        assertThat(redacted).contains("[REDACTED]");
        assertThat(redacted).doesNotContain("550e8400-e29b-41d4-a716-446655440000");
    }
    
    @Test
    void shouldNotRedactNormalText() {
        // Given
        String message = "Normal log message without secrets";
        
        // When
        String redacted = SecretRedactor.redactSecrets(message);
        
        // Then
        assertThat(redacted).isEqualTo(message);
        assertThat(redacted).doesNotContain("[REDACTED]");
    }
    
    @Test
    void shouldRedactMultipleSensitiveValues() {
        // Given
        String message = "Processing: secret=abc123, key=def456, token=ghi789";
        
        // When
        String redacted = SecretRedactor.redactSecrets(message);
        
        // Then
        assertThat(redacted).contains("[REDACTED]");
        assertThat(redacted).doesNotContain("abc123");
        assertThat(redacted).doesNotContain("def456");
        assertThat(redacted).doesNotContain("ghi789");
    }
    
    @Test
    void shouldHandleNullMessage() {
        // Given
        String message = null;
        
        // When
        String redacted = SecretRedactor.redactSecrets(message);
        
        // Then
        assertThat(redacted).isNull();
    }
    
    @Test
    void shouldHandleEmptyMessage() {
        // Given
        String message = "";
        
        // When
        String redacted = SecretRedactor.redactSecrets(message);
        
        // Then
        assertThat(redacted).isEmpty();
    }
    
    @Test
    void shouldRedactExceptionMessages() {
        // Given
        Exception exception = new RuntimeException("Failed with secret: mySecret123");
        
        // When
        String redacted = SecretRedactor.redactExceptionMessage(exception);
        
        // Then
        assertThat(redacted).contains("[REDACTED]");
        assertThat(redacted).doesNotContain("mySecret123");
    }
    
    @Test
    void shouldRedactVersionPrefixedSecrets() {
        // Given
        String message = "Stored value: 20240115:YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY=";
        
        // When
        String redacted = SecretRedactor.redactSecrets(message);
        
        // Then
        assertThat(redacted).contains("[REDACTED]");
        assertThat(redacted).doesNotContain("YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY=");
    }
    
    @Test
    void encoderShouldBeConfigurable() {
        // Verify encoder can be configured and started
        assertThat(encoder.isStarted()).isTrue();
        assertThat(encoder.getContext()).isEqualTo(loggerContext);
    }
}
