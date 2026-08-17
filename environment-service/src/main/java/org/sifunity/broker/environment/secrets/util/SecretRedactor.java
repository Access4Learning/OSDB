// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.util;

import java.util.regex.Pattern;

/**
 * Utility class for redacting sensitive data from strings, exception messages, and stack traces.
 * 
 * <p>This utility helps prevent accidental exposure of secrets, encryption keys, tokens, and other
 * sensitive data in logs, error messages, and stack traces. It identifies patterns that look like
 * sensitive data and replaces them with "[REDACTED]" placeholders.
 * 
 * <p>Patterns detected and redacted:
 * <ul>
 *   <li>Base64-encoded strings (potential encrypted secrets or keys)</li>
 *   <li>UUIDs (potential secret identifiers)</li>
 *   <li>Long hexadecimal strings (potential keys or tokens)</li>
 *   <li>JWT tokens</li>
 *   <li>Known secret field values in key-value pairs</li>
 * </ul>
 * 
 * <p>All methods are static and thread-safe. Null inputs are handled gracefully by returning null.
 * 
 * <p>Example usage:
 * <pre>
 * String message = "Failed to decrypt secret: abc123xyz456";
 * String redacted = SecretRedactor.redactSecrets(message);
 * // Result: "Failed to decrypt secret: [REDACTED]"
 * 
 * Exception ex = new RuntimeException("Invalid key: " + secretKey);
 * String safeMessage = SecretRedactor.redactExceptionMessage(ex);
 * // Result: "Invalid key: [REDACTED]"
 * </pre>
 * 
 * @see org.sifunity.broker.environment.secrets.SecretsBackend
 */
public final class SecretRedactor {
    
    private static final String REDACTED = "[REDACTED]";
    
    // Pattern for Base64 strings (at least 20 characters to avoid false positives)
    // Matches base64 with optional padding, but not if it's part of a longer word
    // Negative lookbehind to avoid matching [REDACTED]
    private static final Pattern BASE64_PATTERN = Pattern.compile(
        "(?<!\\[)\\b[A-Za-z0-9+/]{20,}={0,2}(?=\\s|$|[,;)}\\]])"
    );
    
    // Pattern for UUIDs
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b"
    );
    
    // Pattern for long hexadecimal strings (potential keys, at least 32 chars)
    private static final Pattern HEX_PATTERN = Pattern.compile(
        "\\b[0-9a-fA-F]{32,}\\b"
    );
    
    // Pattern for JWT tokens (three base64 segments separated by dots)
    private static final Pattern JWT_PATTERN = Pattern.compile(
        "\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b"
    );
    
    // Pattern for key-value pairs with sensitive field names
    // Captures the field name and value, handling quotes and various delimiters
    // Explicitly excludes [ to avoid matching [REDACTED]
    private static final Pattern SENSITIVE_FIELD_PATTERN = Pattern.compile(
        "(?i)(secret|password|token|key|credential|auth)\\s*[:=]\\s*(['\"]?)([^'\"\\s,}\\[\\]]+)\\2",
        Pattern.CASE_INSENSITIVE
    );
    
    // Pattern for values after sensitive context words (e.g., "decrypt: value", "failed for secret: value")
    // Explicitly excludes [ to avoid matching [REDACTED]
    private static final Pattern SENSITIVE_CONTEXT_PATTERN = Pattern.compile(
        "(?i)(decrypt|encrypt|secret|password|token|key|credential)\\s*:\\s*([^\\s,}\\[\\]]+)",
        Pattern.CASE_INSENSITIVE
    );
    
    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private SecretRedactor() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    /**
     * Redacts sensitive data from a string.
     * 
     * <p>This method identifies and replaces patterns that look like secrets, keys, tokens,
     * or other sensitive data with "[REDACTED]" placeholders.
     * 
     * @param input the string to redact, may be null
     * @return the redacted string, or null if input is null
     */
    public static String redactSecrets(String input) {
        if (input == null) {
            return null;
        }
        
        String result = input;
        
        // Redact sensitive field values first (e.g., "secret: abc123" -> "secret: [REDACTED]")
        // This pattern captures: field name, optional quotes, value, closing quote
        result = SENSITIVE_FIELD_PATTERN.matcher(result).replaceAll("$1: [REDACTED]");
        
        // Redact values after sensitive context words (e.g., "decrypt: value" -> "decrypt: [REDACTED]")
        result = SENSITIVE_CONTEXT_PATTERN.matcher(result).replaceAll("$1: [REDACTED]");
        
        // Redact JWT tokens
        result = JWT_PATTERN.matcher(result).replaceAll(REDACTED);
        
        // Redact Base64 strings
        result = BASE64_PATTERN.matcher(result).replaceAll(REDACTED);
        
        // Redact UUIDs (potential secret identifiers)
        result = UUID_PATTERN.matcher(result).replaceAll(REDACTED);
        
        // Redact long hexadecimal strings
        result = HEX_PATTERN.matcher(result).replaceAll(REDACTED);
        
        return result;
    }
    
    /**
     * Redacts sensitive data from an exception message.
     * 
     * <p>This method extracts the exception message and applies secret redaction to it.
     * The original exception is not modified.
     * 
     * @param exception the exception whose message should be redacted, may be null
     * @return the redacted exception message, or null if exception is null or has no message
     */
    public static String redactExceptionMessage(Throwable exception) {
        if (exception == null) {
            return null;
        }
        
        String message = exception.getMessage();
        return redactSecrets(message);
    }
    
    /**
     * Redacts sensitive data from an exception's full stack trace.
     * 
     * <p>This method converts the exception's stack trace to a string and applies secret
     * redaction to it. This is useful for logging exceptions without exposing sensitive
     * data that might be embedded in the stack trace.
     * 
     * <p>The redacted output includes:
     * <ul>
     *   <li>Exception class name and redacted message</li>
     *   <li>All stack trace elements with redacted content</li>
     *   <li>Caused by exceptions with redacted messages</li>
     * </ul>
     * 
     * @param exception the exception whose stack trace should be redacted, may be null
     * @return the redacted stack trace as a string, or null if exception is null
     */
    public static String redactStackTrace(Throwable exception) {
        if (exception == null) {
            return null;
        }
        
        StringBuilder sb = new StringBuilder();
        
        // Build stack trace string
        appendException(sb, exception);
        
        // Redact the entire stack trace string
        return redactSecrets(sb.toString());
    }
    
    /**
     * Recursively appends exception information to a StringBuilder.
     * 
     * @param sb the StringBuilder to append to
     * @param exception the exception to append
     */
    private static void appendException(StringBuilder sb, Throwable exception) {
        if (exception == null) {
            return;
        }
        
        // Append exception class and message
        sb.append(exception.getClass().getName());
        if (exception.getMessage() != null) {
            sb.append(": ").append(exception.getMessage());
        }
        sb.append("\n");
        
        // Append stack trace elements
        for (StackTraceElement element : exception.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }
        
        // Append cause if present
        Throwable cause = exception.getCause();
        if (cause != null) {
            sb.append("Caused by: ");
            appendException(sb, cause);
        }
        
        // Append suppressed exceptions if present
        for (Throwable suppressed : exception.getSuppressed()) {
            sb.append("Suppressed: ");
            appendException(sb, suppressed);
        }
    }
}
