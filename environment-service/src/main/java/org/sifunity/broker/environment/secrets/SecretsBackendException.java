// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets;

/**
 * Base exception for secrets backend operations.
 * 
 * <p>This is the root exception for all errors that occur during secrets backend operations.
 * All backend implementations should throw this exception or one of its subclasses when
 * operations fail.
 * 
 * <p>The exception hierarchy provides consistent error handling across all backend
 * implementations:
 * <ul>
 *   <li>{@link SecretNotFoundException} - secret doesn't exist for the given environment</li>
 *   <li>{@link BackendUnavailableException} - backend service is down or unreachable</li>
 *   <li>{@link EncryptionException} - encryption or decryption operation failed</li>
 *   <li>{@link ConfigurationException} - invalid backend configuration</li>
 * </ul>
 * 
 * <p><b>Security Considerations:</b> Exception messages should never contain sensitive
 * data such as secret values, encryption keys, or authentication tokens. Implementations
 * must ensure that error messages are safe to log and display to operators.
 * 
 * @see SecretsBackend
 * @since 1.0
 */
public class SecretsBackendException extends RuntimeException {
    
    /**
     * Constructs a new secrets backend exception with the specified detail message.
     * 
     * @param message the detail message explaining the error. Should not contain
     *                sensitive data such as secret values or encryption keys.
     */
    public SecretsBackendException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new secrets backend exception with the specified detail message
     * and cause.
     * 
     * <p>This constructor is useful for wrapping lower-level exceptions (such as
     * database errors, network errors, or cryptographic exceptions) while providing
     * additional context about the operation that failed.
     * 
     * @param message the detail message explaining the error. Should not contain
     *                sensitive data such as secret values or encryption keys.
     * @param cause the underlying cause of this exception. May be null if no
     *              underlying cause exists.
     */
    public SecretsBackendException(String message, Throwable cause) {
        super(message, cause);
    }
}
