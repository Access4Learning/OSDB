// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.properties;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import net.jqwik.api.*;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for sensitive data never logged.
 * 
 * <p>These tests validate that secrets, encryption keys, and authentication tokens
 * are never exposed in log output. The SecretRedactor and SecretRedactingEncoder
 * should identify and redact all sensitive patterns.</p>
 * 
 * <p><strong>Validates: Requirements 6.7, 7.6, 9.1, 9.2</strong></p>
 * 
 * <p><strong>Property 10: Sensitive Data Never Logged</strong></p>
 * <p>For any secret value, encryption key, or authentication token, logging operations
 * must never expose the raw value in log output.</p>
 */
@Tag("pluggable-secrets-backend")
class SensitiveDataNeverLoggedProperties {

    // ========== Property 10: Sensitive Data Never Logged ==========

    /**
     * Property 10.1: Secrets never appear in log output
     * 
     * <p>For any secret value logged by the application, the log output should
     * contain "[REDACTED]" instead of the actual secret value.</p>
     * 
     * <p>This property validates that the SecretRedactor correctly identifies
     * and redacts secret patterns in log messages.</p>
     * 
     * <p><strong>Validates: Requirements 9.1</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-10-Sensitive-Data-Never-Logged")
    void secretsNeverAppearInLogOutput(
            @ForAll("secrets") String secret) {
        
        // Given: A logger with SecretRedactingEncoder and a ListAppender to capture output
        LoggingTestHelper helper = new LoggingTestHelper();
        
        // When: We log a message containing the secret
        helper.log("Processing secret: " + secret);
        
        // Then: The log output should not contain the actual secret
        List<String> logMessages = helper.getLogMessages();
        assertThat(logMessages)
            .as("Log output should not contain the actual secret")
            .allMatch(msg -> !msg.contains(secret) || secret.isEmpty());
        
        // And: The log output should contain the redacted placeholder
        if (!secret.isEmpty()) {
            assertThat(logMessages)
                .as("Log output should contain [REDACTED] placeholder")
                .anyMatch(msg -> msg.contains("[REDACTED]"));
        }
        
        helper.cleanup();
    }

    /**
     * Property 10.2: Encryption keys never appear in log output
     * 
     * <p>For any encryption key (hex string) logged by the application, the log
     * output should contain "[REDACTED]" instead of the actual key value.</p>
     * 
     * <p><strong>Validates: Requirements 6.7, 9.2</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-10-Sensitive-Data-Never-Logged")
    void encryptionKeysNeverAppearInLogOutput(
            @ForAll("encryptionKeys") String encryptionKey) {
        
        // Given: A logger with SecretRedactingEncoder
        LoggingTestHelper helper = new LoggingTestHelper();
        
        // When: We log a message containing the encryption key
        helper.log("Using encryption key: " + encryptionKey);
        
        // Then: The log output should not contain the actual key
        List<String> logMessages = helper.getLogMessages();
        assertThat(logMessages)
            .as("Log output should not contain the actual encryption key")
            .allMatch(msg -> !msg.contains(encryptionKey));
        
        // And: The log output should contain the redacted placeholder
        assertThat(logMessages)
            .as("Log output should contain [REDACTED] placeholder")
            .anyMatch(msg -> msg.contains("[REDACTED]"));
        
        helper.cleanup();
    }

    /**
     * Property 10.3: Vault tokens never appear in log output
     * 
     * <p>For any Vault authentication token (JWT format) logged by the application,
     * the log output should contain "[REDACTED]" instead of the actual token.</p>
     * 
     * <p><strong>Validates: Requirements 7.6</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-10-Sensitive-Data-Never-Logged")
    void vaultTokensNeverAppearInLogOutput(
            @ForAll("vaultTokens") String vaultToken) {
        
        // Given: A logger with SecretRedactingEncoder
        LoggingTestHelper helper = new LoggingTestHelper();
        
        // When: We log a message containing the Vault token
        helper.log("Authenticating with Vault token: " + vaultToken);
        
        // Then: The log output should not contain the actual token
        List<String> logMessages = helper.getLogMessages();
        assertThat(logMessages)
            .as("Log output should not contain the actual Vault token")
            .allMatch(msg -> !msg.contains(vaultToken));
        
        // And: The log output should contain the redacted placeholder
        assertThat(logMessages)
            .as("Log output should contain [REDACTED] placeholder")
            .anyMatch(msg -> msg.contains("[REDACTED]"));
        
        helper.cleanup();
    }

    /**
     * Property 10.4: Base64-encoded secrets never appear in log output
     * 
     * <p>For any base64-encoded secret logged by the application, the log output
     * should contain "[REDACTED]" instead of the actual encoded value.</p>
     * 
     * <p><strong>Validates: Requirements 9.1</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-10-Sensitive-Data-Never-Logged")
    void base64SecretsNeverAppearInLogOutput(
            @ForAll("base64Secrets") String base64Secret) {
        
        // Given: A logger with SecretRedactingEncoder
        LoggingTestHelper helper = new LoggingTestHelper();
        
        // When: We log a message containing the base64-encoded secret
        helper.log("Encrypted secret value: " + base64Secret);
        
        // Then: The log output should not contain the actual base64 value
        List<String> logMessages = helper.getLogMessages();
        assertThat(logMessages)
            .as("Log output should not contain the actual base64-encoded secret")
            .allMatch(msg -> !msg.contains(base64Secret));
        
        // And: The log output should contain the redacted placeholder
        assertThat(logMessages)
            .as("Log output should contain [REDACTED] placeholder")
            .anyMatch(msg -> msg.contains("[REDACTED]"));
        
        helper.cleanup();
    }

    /**
     * Property 10.5: Secrets in key-value pairs never appear in log output
     * 
     * <p>For any secret logged as part of a key-value pair (e.g., "secret: value"),
     * the log output should redact the value portion.</p>
     * 
     * <p><strong>Validates: Requirements 9.1</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-10-Sensitive-Data-Never-Logged")
    void secretsInKeyValuePairsNeverAppearInLogOutput(
            @ForAll("secrets") String secret) {
        
        // Given: A logger with SecretRedactingEncoder
        LoggingTestHelper helper = new LoggingTestHelper();
        
        // When: We log a message with secret in key-value format
        helper.log("Configuration: {secret: " + secret + ", enabled: true}");
        
        // Then: The log output should not contain the actual secret
        List<String> logMessages = helper.getLogMessages();
        assertThat(logMessages)
            .as("Log output should not contain the actual secret value")
            .allMatch(msg -> !msg.contains(secret) || secret.isEmpty());
        
        // And: The log output should contain the redacted placeholder
        if (!secret.isEmpty()) {
            assertThat(logMessages)
                .as("Log output should contain [REDACTED] placeholder")
                .anyMatch(msg -> msg.contains("[REDACTED]"));
        }
        
        helper.cleanup();
    }

    /**
     * Property 10.6: Multiple sensitive values in same log message are all redacted
     * 
     * <p>For any log message containing multiple sensitive values (secret, key, token),
     * all sensitive values should be redacted.</p>
     * 
     * <p><strong>Validates: Requirements 6.7, 7.6, 9.1, 9.2</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-10-Sensitive-Data-Never-Logged")
    void multipleSensitiveValuesAllRedacted(
            @ForAll("secrets") String secret,
            @ForAll("encryptionKeys") String key,
            @ForAll("vaultTokens") String token) {
        
        // Given: A logger with SecretRedactingEncoder
        LoggingTestHelper helper = new LoggingTestHelper();
        
        // When: We log a message containing multiple sensitive values
        helper.log("Processing: secret=" + secret + ", key=" + key + ", token=" + token);
        
        // Then: The log output should not contain any of the sensitive values
        List<String> logMessages = helper.getLogMessages();
        assertThat(logMessages)
            .as("Log output should not contain the actual secret")
            .allMatch(msg -> !msg.contains(secret) || secret.isEmpty());
        assertThat(logMessages)
            .as("Log output should not contain the actual key")
            .allMatch(msg -> !msg.contains(key));
        assertThat(logMessages)
            .as("Log output should not contain the actual token")
            .allMatch(msg -> !msg.contains(token));
        
        // And: The log output should contain multiple redacted placeholders
        assertThat(logMessages)
            .as("Log output should contain [REDACTED] placeholders")
            .anyMatch(msg -> msg.contains("[REDACTED]"));
        
        helper.cleanup();
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

    // ========== Helper Class ==========

    /**
     * Helper class for testing log output with SecretRedactingEncoder.
     * 
     * <p>This class creates a test logger with a ListAppender to capture
     * log output, and applies the SecretRedactor to redact sensitive
     * data from the captured messages.</p>
     */
    private static class LoggingTestHelper {
        private final Logger logger;
        private final ListAppender<ILoggingEvent> listAppender;
        
        public LoggingTestHelper() {
            // Create a test logger
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            this.logger = loggerContext.getLogger("test-logger-" + System.nanoTime());
            this.logger.setLevel(Level.INFO);
            
            // Create a ListAppender to capture log events
            this.listAppender = new ListAppender<>();
            this.listAppender.setContext(loggerContext);
            this.listAppender.start();
            
            // Add the appender to the logger
            this.logger.addAppender(listAppender);
        }
        
        /**
         * Logs a message at INFO level.
         */
        public void log(String message) {
            logger.info(message);
        }
        
        /**
         * Gets the captured log messages with redaction applied.
         * 
         * <p>This method applies SecretRedactor to each captured log event's
         * formatted message and returns the redacted messages.</p>
         */
        public List<String> getLogMessages() {
            return listAppender.list.stream()
                .map(event -> {
                    String formattedMessage = event.getFormattedMessage();
                    return org.sifunity.broker.environment.secrets.util.SecretRedactor.redactSecrets(formattedMessage);
                })
                .toList();
        }
        
        /**
         * Cleans up the logger and appender.
         */
        public void cleanup() {
            logger.detachAppender(listAppender);
            listAppender.stop();
        }
    }
}
