// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets;

/**
 * Exception thrown when a secrets backend service is unavailable or unreachable.
 * 
 * <p>This exception indicates that the backend service required for secrets operations
 * is temporarily or permanently unavailable. This is typically used by the
 * {@link org.sifunity.broker.environment.secrets.impl.VaultBackend VaultBackend}
 * implementation when HashiCorp Vault cannot be reached.
 * 
 * <p><b>Common Scenarios:</b>
 * <ul>
 *   <li>Vault server is down or unreachable due to network issues</li>
 *   <li>Vault authentication token has expired or is invalid</li>
 *   <li>Vault is experiencing high load and timing out requests</li>
 *   <li>Network connectivity issues between the application and Vault</li>
 *   <li>Vault is in sealed state and cannot serve requests</li>
 *   <li>DNS resolution failure for Vault hostname</li>
 * </ul>
 * 
 * <p><b>Error Handling:</b> This exception typically indicates a transient failure
 * that may be resolved by:
 * <ul>
 *   <li>Implementing retry logic with exponential backoff</li>
 *   <li>Using circuit breaker patterns to prevent cascading failures</li>
 *   <li>Falling back to cached credentials if appropriate</li>
 *   <li>Returning 503 Service Unavailable to clients</li>
 *   <li>Triggering alerts for operations teams</li>
 * </ul>
 * 
 * <p><b>Circuit Breaker Integration:</b> The VaultBackend implementation uses a
 * circuit breaker that opens after consecutive failures and prevents further
 * requests until the service recovers. This exception is thrown when the circuit
 * is open.
 * 
 * <p><b>Example Usage:</b>
 * <pre>
 * try {
 *     secretsBackend.store(environmentId, secret);
 * } catch (BackendUnavailableException e) {
 *     log.error("Vault backend unavailable: {}", e.getMessage());
 *     // Trigger alert for operations team
 *     alertService.sendAlert("Vault unavailable", e);
 *     return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
 * }
 * </pre>
 * 
 * @see SecretsBackend
 * @since 1.0
 */
public class BackendUnavailableException extends SecretsBackendException {
    
    /**
     * Constructs a new backend unavailable exception with the specified detail message.
     * 
     * <p>The message should describe why the backend is unavailable (e.g., "Vault server
     * unreachable", "Authentication failed") but should not include sensitive information
     * such as authentication tokens or internal network details.
     * 
     * @param message the detail message explaining why the backend is unavailable.
     *                Should not contain sensitive data.
     */
    public BackendUnavailableException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new backend unavailable exception with the specified detail message
     * and cause.
     * 
     * <p>This constructor is useful for wrapping lower-level exceptions such as network
     * errors, connection timeouts, or authentication failures while providing additional
     * context about the backend service that is unavailable.
     * 
     * <p><b>Example:</b>
     * <pre>
     * try {
     *     vaultTemplate.write(path, data);
     * } catch (VaultException e) {
     *     throw new BackendUnavailableException("Failed to connect to Vault", e);
     * }
     * </pre>
     * 
     * @param message the detail message explaining why the backend is unavailable.
     *                Should not contain sensitive data such as authentication tokens.
     * @param cause the underlying cause of the unavailability (e.g., network exception,
     *              authentication exception). May be null if no underlying cause exists.
     */
    public BackendUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
