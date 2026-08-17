// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets;

import java.util.UUID;

/**
 * Abstraction for secrets storage backends in the SIF Unity Broker.
 * 
 * <p>This interface defines the contract for managing shared secrets used in environment
 * authentication. Implementations provide different security levels to accommodate various
 * deployment scenarios:
 * 
 * <ul>
 *   <li><b>PlaintextBackend</b>: Stores secrets without encryption. Suitable for development
 *       and testing environments only. Logs a warning on initialization to alert operators
 *       that secrets are stored in plain text.</li>
 *   <li><b>MultiKeyBackend</b>: Provides AES-256-GCM encryption with support for multiple
 *       key versions, enabling key rotation without service downtime. Secrets are stored
 *       with a version prefix (YYYYMMDD:encrypted_data) to identify which key was used
 *       for encryption.</li>
 *   <li><b>VaultBackend</b>: Integrates with HashiCorp Vault for enterprise-grade secrets
 *       management. Only reference IDs are stored in the database, while actual secrets
 *       reside in Vault.</li>
 * </ul>
 * 
 * <p><b>Design Principles:</b>
 * <ul>
 *   <li>Backend selection is configuration-driven via Spring Boot properties</li>
 *   <li>All implementations throw consistent exception types for error handling</li>
 *   <li>Secrets are never exposed in logs, error messages, or API responses (except during
 *       environment registration)</li>
 *   <li>The interface supports backward compatibility when migrating between backends</li>
 * </ul>
 * 
 * <p><b>Thread Safety:</b> Implementations must be thread-safe as they will be used
 * concurrently by multiple request handlers.
 * 
 * <p><b>Error Handling:</b> All methods may throw:
 * <ul>
 *   <li>{@link SecretNotFoundException} - when a secret doesn't exist for the given environment</li>
 *   <li>{@link SecretsBackendException} - for backend-specific failures (encryption errors,
 *       Vault connectivity issues, etc.)</li>
 * </ul>
 * 
 * <p><b>Configuration Example:</b>
 * <pre>
 * # Plaintext Backend (default)
 * secrets.backend.type: plaintext
 * 
 * # Multi-Key Backend
 * secrets.backend.type: multi-key
 * secrets.backend.multi-key.current-version: "20240115"
 * secrets.backend.multi-key.keys:
 *   "20240115": "${ENCRYPTION_KEY_20240115}"
 *   "20240101": "${ENCRYPTION_KEY_20240101}"
 * 
 * # Vault Backend
 * secrets.backend.type: vault
 * secrets.backend.vault.url: "https://vault.example.com:8200"
 * secrets.backend.vault.token: "${VAULT_TOKEN}"
 * secrets.backend.vault.secrets-path: "secret/sif-broker"
 * </pre>
 * 
 * @see SecretsBackendException
 * @see SecretNotFoundException
 * @since 1.0
 */
public interface SecretsBackend {
    
    /**
     * Stores a secret for the given environment.
     * 
     * <p>The storage mechanism depends on the implementation:
     * <ul>
     *   <li><b>PlaintextBackend</b>: Stores the secret directly in the database without
     *       any transformation</li>
     *   <li><b>MultiKeyBackend</b>: Encrypts the secret using AES-256-GCM with the current
     *       encryption key, then stores it with a version prefix (YYYYMMDD:encrypted_data)</li>
     *   <li><b>VaultBackend</b>: Writes the secret to Vault and stores only the Vault
     *       path reference in the database</li>
     * </ul>
     * 
     * <p><b>Performance Expectations:</b>
     * <ul>
     *   <li>PlaintextBackend: &lt; 10ms</li>
     *   <li>MultiKeyBackend: &lt; 50ms</li>
     *   <li>VaultBackend: &lt; 200ms (network dependent)</li>
     * </ul>
     * 
     * <p><b>Security Considerations:</b>
     * <ul>
     *   <li>The secret value is never logged by any implementation</li>
     *   <li>For MultiKeyBackend, encryption keys are never logged</li>
     *   <li>For VaultBackend, authentication tokens are never logged</li>
     * </ul>
     * 
     * @param environmentId the unique identifier of the environment for which to store
     *                      the secret. Must not be null.
     * @param secret the secret value to store. Must not be null or empty. The secret
     *               should be a securely generated random string suitable for authentication.
     * @throws SecretNotFoundException if the environment with the given ID doesn't exist
     * @throws SecretsBackendException if the storage operation fails due to backend-specific
     *                                 errors (e.g., encryption failure, Vault unavailable,
     *                                 database connection failure)
     * @throws IllegalArgumentException if environmentId or secret is null
     */
    void store(UUID environmentId, String secret);
    
    /**
     * Retrieves a secret for the given environment.
     * 
     * <p>The retrieval mechanism depends on the implementation:
     * <ul>
     *   <li><b>PlaintextBackend</b>: Returns the secret directly from the database</li>
     *   <li><b>MultiKeyBackend</b>: Extracts the version prefix from the stored value,
     *       looks up the corresponding encryption key, and decrypts the secret. Supports
     *       decryption of secrets encrypted with any configured key version, enabling
     *       seamless key rotation.</li>
     *   <li><b>VaultBackend</b>: Uses the stored reference ID to fetch the secret from
     *       Vault</li>
     * </ul>
     * 
     * <p><b>Backward Compatibility:</b> The MultiKeyBackend implementation handles legacy
     * plaintext secrets (those without a version prefix) by returning them as-is. This
     * allows for gradual migration from PlaintextBackend to MultiKeyBackend without
     * service disruption.
     * 
     * <p><b>Performance Expectations:</b>
     * <ul>
     *   <li>PlaintextBackend: &lt; 10ms</li>
     *   <li>MultiKeyBackend: &lt; 50ms</li>
     *   <li>VaultBackend: &lt; 200ms (network dependent)</li>
     * </ul>
     * 
     * <p><b>Security Considerations:</b>
     * <ul>
     *   <li>The retrieved secret should only be used for authentication purposes</li>
     *   <li>Callers must ensure the secret is not logged or exposed in API responses
     *       (except during environment registration)</li>
     *   <li>The secret should be cleared from memory as soon as it's no longer needed</li>
     * </ul>
     * 
     * @param environmentId the unique identifier of the environment for which to retrieve
     *                      the secret. Must not be null.
     * @return the secret value in plaintext. Never returns null.
     * @throws SecretNotFoundException if no secret exists for the given environment ID
     * @throws SecretsBackendException if the retrieval operation fails due to backend-specific
     *                                 errors (e.g., decryption failure, missing encryption key
     *                                 version, Vault unavailable, database connection failure)
     * @throws IllegalArgumentException if environmentId is null
     */
    String retrieve(UUID environmentId);
    
    /**
     * Deletes a secret for the given environment.
     * 
     * <p>The deletion mechanism depends on the implementation:
     * <ul>
     *   <li><b>PlaintextBackend</b>: Sets the secret field to null in the database</li>
     *   <li><b>MultiKeyBackend</b>: Sets the secret field to null in the database. The
     *       encrypted value is permanently removed and cannot be recovered.</li>
     *   <li><b>VaultBackend</b>: Deletes the secret from Vault and removes the reference
     *       ID from the database</li>
     * </ul>
     * 
     * <p><b>Idempotency:</b> This operation is idempotent. Calling delete multiple times
     * for the same environment ID will not result in an error, though subsequent calls
     * after the first will have no effect.
     * 
     * <p><b>Use Cases:</b>
     * <ul>
     *   <li>Environment deregistration</li>
     *   <li>Secret rotation (delete old secret, store new secret)</li>
     *   <li>Cleanup operations</li>
     * </ul>
     * 
     * @param environmentId the unique identifier of the environment for which to delete
     *                      the secret. Must not be null.
     * @throws SecretNotFoundException if the environment with the given ID doesn't exist
     * @throws SecretsBackendException if the deletion operation fails due to backend-specific
     *                                 errors (e.g., Vault unavailable, database connection failure)
     * @throws IllegalArgumentException if environmentId is null
     */
    void delete(UUID environmentId);
    
    /**
     * Checks if the backend is healthy and operational.
     * 
     * <p>Health check criteria vary by implementation:
     * <ul>
     *   <li><b>PlaintextBackend</b>: Always returns true (no external dependencies)</li>
     *   <li><b>MultiKeyBackend</b>: Returns true if at least one encryption key is
     *       configured and the current key version is available</li>
     *   <li><b>VaultBackend</b>: Returns true if Vault is reachable and authentication
     *       is valid. Uses Vault's health endpoint for verification.</li>
     * </ul>
     * 
     * <p><b>Usage:</b> This method is typically called by:
     * <ul>
     *   <li>Health check endpoints to report overall system health</li>
     *   <li>Circuit breakers to determine if the backend should be used</li>
     *   <li>Monitoring systems to track backend availability</li>
     * </ul>
     * 
     * <p><b>Performance:</b> This method should complete quickly (typically &lt; 100ms)
     * to avoid blocking health check requests. Implementations may cache health status
     * for a short period to reduce overhead.
     * 
     * <p><b>Non-Blocking:</b> This method should not throw exceptions. If a health check
     * fails, the method should return false rather than throwing an exception.
     * 
     * @return true if the backend is healthy and can process requests, false otherwise
     */
    boolean isHealthy();
    
    /**
     * Returns the backend type identifier.
     * 
     * <p>This method returns a string identifier that indicates which backend implementation
     * is currently active. The identifier matches the configuration property value used to
     * select the backend.
     * 
     * <p><b>Valid Return Values:</b>
     * <ul>
     *   <li>"plaintext" - PlaintextBackend</li>
     *   <li>"multi-key" - MultiKeyBackend</li>
     *   <li>"vault" - VaultBackend</li>
     * </ul>
     * 
     * <p><b>Usage:</b> This method is useful for:
     * <ul>
     *   <li>Logging the active backend during application startup</li>
     *   <li>Exposing backend information in admin/info endpoints</li>
     *   <li>Conditional logic that depends on the backend type (though this should be
     *       minimized to maintain abstraction)</li>
     *   <li>Monitoring and observability to track which backend is in use</li>
     * </ul>
     * 
     * @return the backend type identifier (e.g., "plaintext", "multi-key", "vault").
     *         Never returns null.
     */
    String getBackendType();
}
