// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SecretRedactor}.
 */
class SecretRedactorTest {
    
    @Test
    void redactSecrets_withNull_returnsNull() {
        assertThat(SecretRedactor.redactSecrets(null)).isNull();
    }
    
    @Test
    void redactSecrets_withEmptyString_returnsEmptyString() {
        assertThat(SecretRedactor.redactSecrets("")).isEmpty();
    }
    
    @Test
    void redactSecrets_withNoSensitiveData_returnsOriginal() {
        String input = "This is a normal log message with no secrets";
        assertThat(SecretRedactor.redactSecrets(input)).isEqualTo(input);
    }
    
    @Test
    void redactSecrets_withBase64String_redactsIt() {
        String input = "Encrypted value: YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXo=";
        String result = SecretRedactor.redactSecrets(input);
        
        assertThat(result).isEqualTo("Encrypted value: [REDACTED]");
        assertThat(result).doesNotContain("YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXo=");
    }
    
    @Test
    void redactSecrets_withShortBase64String_doesNotRedact() {
        // Short base64 strings (< 20 chars) are not redacted to avoid false positives
        String input = "Short value: abc123";
        assertThat(SecretRedactor.redactSecrets(input)).isEqualTo(input);
    }
    
    @Test
    void redactSecrets_withUUID_redactsIt() {
        String input = "Environment ID: 550e8400-e29b-41d4-a716-446655440000";
        String result = SecretRedactor.redactSecrets(input);
        
        assertThat(result).isEqualTo("Environment ID: [REDACTED]");
        assertThat(result).doesNotContain("550e8400-e29b-41d4-a716-446655440000");
    }
    
    @Test
    void redactSecrets_withHexString_redactsIt() {
        String input = "Encryption key: 0123456789abcdef0123456789abcdef0123456789abcdef";
        String result = SecretRedactor.redactSecrets(input);
        
        assertThat(result).isEqualTo("Encryption key: [REDACTED]");
        assertThat(result).doesNotContain("0123456789abcdef");
    }
    
    @Test
    void redactSecrets_withShortHexString_doesNotRedact() {
        // Short hex strings (< 32 chars) are not redacted to avoid false positives
        String input = "Short hex: abc123def456";
        assertThat(SecretRedactor.redactSecrets(input)).isEqualTo(input);
    }
    
    @Test
    void redactSecrets_withJWTToken_redactsIt() {
        String input = "Authorization: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U";
        String result = SecretRedactor.redactSecrets(input);
        
        assertThat(result).isEqualTo("Authorization: [REDACTED]");
        assertThat(result).doesNotContain("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9");
    }
    
    @Test
    void redactSecrets_withSecretFieldColon_redactsValue() {
        String input = "Configuration: secret: mySecretValue123";
        String result = SecretRedactor.redactSecrets(input);
        
        assertThat(result).isEqualTo("Configuration: secret: [REDACTED]");
        assertThat(result).doesNotContain("mySecretValue123");
    }
    
    @Test
    void redactSecrets_withSecretFieldEquals_redactsValue() {
        String input = "Config: secret=mySecretValue123";
        String result = SecretRedactor.redactSecrets(input);
        
        assertThat(result).isEqualTo("Config: secret: [REDACTED]");
        assertThat(result).doesNotContain("mySecretValue123");
    }
    
    @Test
    void redactSecrets_withPasswordField_redactsValue() {
        String input = "Login failed: password: wrongPassword123";
        String result = SecretRedactor.redactSecrets(input);
        
        assertThat(result).isEqualTo("Login failed: password: [REDACTED]");
        assertThat(result).doesNotContain("wrongPassword123");
    }
    
    @Test
    void redactSecrets_withTokenField_redactsValue() {
        String input = "API call: token=abc123xyz456";
        String result = SecretRedactor.redactSecrets(input);
        
        assertThat(result).isEqualTo("API call: token: [REDACTED]");
        assertThat(result).doesNotContain("abc123xyz456");
    }
    
    @Test
    void redactSecrets_withKeyField_redactsValue() {
        String input = "Encryption: key: 0123456789abcdef";
        String result = SecretRedactor.redactSecrets(input);
        
        assertThat(result).isEqualTo("Encryption: key: [REDACTED]");
        assertThat(result).doesNotContain("0123456789abcdef");
    }
    
    @Test
    void redactSecrets_withCredentialField_redactsValue() {
        String input = "Auth: credential:myCredential123";
        String result = SecretRedactor.redactSecrets(input);
        
        // The word "credential" itself matches the pattern, so it gets redacted
        assertThat(result).isEqualTo("Auth: [REDACTED]");
        assertThat(result).doesNotContain("myCredential123");
        assertThat(result).doesNotContain("credential");
    }
    
    @Test
    void redactSecrets_withAuthField_redactsValue() {
        String input = "Header: auth='BearerToken123'";
        String result = SecretRedactor.redactSecrets(input);
        
        assertThat(result).isEqualTo("Header: auth: [REDACTED]");
        assertThat(result).doesNotContain("BearerToken123");
    }
    
    @Test
    void redactSecrets_withMultipleSensitivePatterns_redactsAll() {
        String input = "Error: secret=abc123, uuid=550e8400-e29b-41d4-a716-446655440000, " +
                      "token: YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXo=";
        String result = SecretRedactor.redactSecrets(input);
        
        assertThat(result).contains("[REDACTED]");
        assertThat(result).doesNotContain("abc123");
        assertThat(result).doesNotContain("550e8400-e29b-41d4-a716-446655440000");
        assertThat(result).doesNotContain("YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXo=");
    }
    
    @Test
    void redactSecrets_withCaseInsensitiveFields_redactsAll() {
        String input = "SECRET: value1, Secret: value2, secret: value3";
        String result = SecretRedactor.redactSecrets(input);
        
        assertThat(result).doesNotContain("value1");
        assertThat(result).doesNotContain("value2");
        assertThat(result).doesNotContain("value3");
    }
    
    @Test
    void redactExceptionMessage_withNull_returnsNull() {
        assertThat(SecretRedactor.redactExceptionMessage(null)).isNull();
    }
    
    @Test
    void redactExceptionMessage_withNullMessage_returnsNull() {
        Exception ex = new RuntimeException();
        assertThat(SecretRedactor.redactExceptionMessage(ex)).isNull();
    }
    
    @Test
    void redactExceptionMessage_withNoSensitiveData_returnsOriginal() {
        Exception ex = new RuntimeException("Simple error message");
        assertThat(SecretRedactor.redactExceptionMessage(ex))
            .isEqualTo("Simple error message");
    }
    
    @Test
    void redactExceptionMessage_withSecret_redactsIt() {
        Exception ex = new RuntimeException("Failed to decrypt secret: YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXo=");
        String result = SecretRedactor.redactExceptionMessage(ex);
        
        assertThat(result).isEqualTo("Failed to decrypt secret: [REDACTED]");
        assertThat(result).doesNotContain("YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXo=");
    }
    
    @Test
    void redactExceptionMessage_withKeyInMessage_redactsIt() {
        Exception ex = new IllegalArgumentException("Invalid key: 0123456789abcdef0123456789abcdef");
        String result = SecretRedactor.redactExceptionMessage(ex);
        
        assertThat(result).contains("[REDACTED]");
        assertThat(result).doesNotContain("0123456789abcdef0123456789abcdef");
    }
    
    @Test
    void redactStackTrace_withNull_returnsNull() {
        assertThat(SecretRedactor.redactStackTrace(null)).isNull();
    }
    
    @Test
    void redactStackTrace_withNoSensitiveData_containsStackTrace() {
        Exception ex = new RuntimeException("Simple error");
        String result = SecretRedactor.redactStackTrace(ex);
        
        assertThat(result).contains("RuntimeException");
        assertThat(result).contains("Simple error");
        assertThat(result).contains("at ");
    }
    
    @Test
    void redactStackTrace_withSecretInMessage_redactsIt() {
        Exception ex = new RuntimeException("Error with secret: YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXo=");
        String result = SecretRedactor.redactStackTrace(ex);
        
        assertThat(result).contains("RuntimeException");
        assertThat(result).contains("[REDACTED]");
        assertThat(result).doesNotContain("YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXo=");
    }
    
    @Test
    void redactStackTrace_withCausedException_includesCause() {
        Exception cause = new IllegalStateException("Cause with token: abc123xyz456token");
        Exception ex = new RuntimeException("Main error", cause);
        String result = SecretRedactor.redactStackTrace(ex);
        
        assertThat(result).contains("RuntimeException");
        assertThat(result).contains("Caused by:");
        assertThat(result).contains("[REDACTED]");
        assertThat(result).doesNotContain("abc123xyz456token");
    }
    
    @Test
    void redactStackTrace_withSuppressedException_includesSuppressed() {
        Exception ex = new RuntimeException("Main error");
        Exception suppressed = new IllegalArgumentException("Suppressed with key: 0123456789abcdef0123456789abcdef");
        ex.addSuppressed(suppressed);
        
        String result = SecretRedactor.redactStackTrace(ex);
        
        assertThat(result).contains("RuntimeException");
        assertThat(result).contains("Suppressed:");
        assertThat(result).contains("[REDACTED]");
        assertThat(result).doesNotContain("0123456789abcdef0123456789abcdef");
    }
    
    @Test
    void redactStackTrace_preservesStackTraceStructure() {
        Exception ex = new RuntimeException("Test error");
        String result = SecretRedactor.redactStackTrace(ex);
        
        assertThat(result).contains("RuntimeException: Test error");
        assertThat(result).contains("\tat ");
        assertThat(result).contains("SecretRedactorTest");
    }
    
    @Test
    void constructor_throwsException() {
        assertThatThrownBy(() -> {
            // Use reflection to call private constructor
            var constructor = SecretRedactor.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        }).hasCauseInstanceOf(UnsupportedOperationException.class)
          .hasRootCauseMessage("Utility class cannot be instantiated");
    }
    
    @Test
    void redactSecrets_withVersionPrefixedSecret_redactsEncryptedPart() {
        // Test with Multi-Key Backend format: YYYYMMDD:base64_encrypted_data
        String input = "Stored value: 20240115:YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXo=";
        String result = SecretRedactor.redactSecrets(input);
        
        assertThat(result).isEqualTo("Stored value: 20240115:[REDACTED]");
        assertThat(result).doesNotContain("YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXo=");
    }
    
    @Test
    void redactSecrets_withVaultPath_redactsUUID() {
        // Vault paths contain UUIDs which will be redacted, but that's acceptable
        // The path structure is preserved
        String input = "Vault path: secret/sif-broker/environment/550e8400-e29b-41d4-a716-446655440000";
        String result = SecretRedactor.redactSecrets(input);
        
        // The UUID in the path will be redacted
        assertThat(result).contains("secret/sif-broker/environment/");
        assertThat(result).contains("[REDACTED]");
        assertThat(result).doesNotContain("550e8400-e29b-41d4-a716-446655440000");
    }
    
    @Test
    void redactSecrets_withMultilineString_redactsAllLines() {
        String input = "Line 1: secret=value1\n" +
                      "Line 2: token: YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXo=\n" +
                      "Line 3: Normal text";
        String result = SecretRedactor.redactSecrets(input);
        
        assertThat(result).doesNotContain("value1");
        assertThat(result).doesNotContain("YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXo=");
        assertThat(result).contains("Normal text");
    }
}
