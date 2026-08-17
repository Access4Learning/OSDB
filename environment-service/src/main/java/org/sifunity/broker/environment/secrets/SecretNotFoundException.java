// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets;

import java.util.UUID;

/**
 * Exception thrown when a secret is not found for a given environment.
 * 
 * <p>This exception is thrown by {@link SecretsBackend} implementations when attempting
 * to retrieve or delete a secret for an environment that either doesn't exist or has
 * no associated secret.
 * 
 * <p><b>Common Scenarios:</b>
 * <ul>
 *   <li>Attempting to retrieve a secret for an environment that was never registered</li>
 *   <li>Attempting to retrieve a secret that was previously deleted</li>
 *   <li>Attempting to delete a secret for a non-existent environment</li>
 *   <li>Database inconsistency where an environment exists but has no secret</li>
 * </ul>
 * 
 * <p><b>Error Handling:</b> Callers should handle this exception appropriately based
 * on the context:
 * <ul>
 *   <li>During authentication: Return 401 Unauthorized</li>
 *   <li>During environment lookup: Return 404 Not Found</li>
 *   <li>During deletion: May be treated as success (idempotent operation)</li>
 * </ul>
 * 
 * <p><b>Example Usage:</b>
 * <pre>
 * try {
 *     String secret = secretsBackend.retrieve(environmentId);
 * } catch (SecretNotFoundException e) {
 *     log.warn("Secret not found for environment: {}", e.getEnvironmentId());
 *     return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
 * }
 * </pre>
 * 
 * @see SecretsBackend#retrieve(UUID)
 * @see SecretsBackend#delete(UUID)
 * @since 1.0
 */
public class SecretNotFoundException extends SecretsBackendException {
    
    /**
     * The environment ID for which the secret was not found.
     */
    private final UUID environmentId;
    
    /**
     * Constructs a new secret not found exception for the given environment.
     * 
     * <p>The exception message is automatically generated to include the environment ID
     * in a format suitable for logging and error reporting.
     * 
     * @param environmentId the unique identifier of the environment for which the secret
     *                      was not found. Must not be null.
     * @throws IllegalArgumentException if environmentId is null
     */
    public SecretNotFoundException(UUID environmentId) {
        super("Secret not found for environment: " + environmentId);
        if (environmentId == null) {
            throw new IllegalArgumentException("environmentId must not be null");
        }
        this.environmentId = environmentId;
    }
    
    /**
     * Returns the environment ID for which the secret was not found.
     * 
     * <p>This method allows exception handlers to access the environment ID for
     * logging, error reporting, or conditional error handling.
     * 
     * @return the environment ID. Never returns null.
     */
    public UUID getEnvironmentId() {
        return environmentId;
    }
}
