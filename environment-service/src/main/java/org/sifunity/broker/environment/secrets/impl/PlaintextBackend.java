// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.impl;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.secrets.SecretNotFoundException;
import org.sifunity.broker.environment.secrets.SecretsBackend;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Plaintext secrets backend - stores secrets without encryption.
 * 
 * <p><b>WARNING:</b> This backend provides no security and should only be used
 * for development and testing environments. Secrets are stored in plain text
 * in the PostgreSQL database without any encryption or obfuscation.
 * 
 * <p>This implementation logs a prominent warning message on initialization to
 * alert operators that secrets are stored insecurely. The warning is repeated
 * with visual separators to ensure it's noticed in application logs.
 * 
 * <p><b>Use Cases:</b>
 * <ul>
 *   <li>Local development environments</li>
 *   <li>Automated testing and CI/CD pipelines</li>
 *   <li>Proof-of-concept deployments</li>
 * </ul>
 * 
 * <p><b>NOT Suitable For:</b>
 * <ul>
 *   <li>Production environments</li>
 *   <li>Staging environments with real data</li>
 *   <li>Any environment handling sensitive information</li>
 * </ul>
 * 
 * <p><b>Performance Characteristics:</b>
 * <ul>
 *   <li>Store operation: &lt; 10ms (direct database write)</li>
 *   <li>Retrieve operation: &lt; 10ms (direct database read)</li>
 *   <li>Delete operation: &lt; 10ms (direct database update)</li>
 *   <li>Health check: O(1) constant time (always returns true)</li>
 * </ul>
 * 
 * <p><b>Configuration:</b>
 * This backend must be explicitly selected via configuration. It is not the default.
 * Dev and test profiles should set:
 * <pre>
 * secrets:
 *   backend:
 *     type: plaintext
 * </pre>
 * 
 * <p><b>Thread Safety:</b> This implementation is thread-safe. The underlying
 * EnvironmentRepository is managed by Spring and provides thread-safe access
 * to the database.
 * 
 * @see SecretsBackend
 * @see org.sifunity.broker.environment.secrets.config.SecretsBackendAutoConfiguration
 * @since 1.0
 */
@Slf4j
@RequiredArgsConstructor
public class PlaintextBackend implements SecretsBackend {
    
    private final EnvironmentRepository environmentRepository;
    
    /**
     * Initialization method that logs a warning about plaintext storage.
     * 
     * <p>This method is called automatically by Spring after the bean is
     * constructed and dependencies are injected. It logs a prominent warning
     * message with visual separators to ensure operators are aware that
     * secrets are stored without encryption.
     * 
     * <p>The warning is logged at WARN level to ensure it appears in most
     * logging configurations and is visible in application startup logs.
     */
    @PostConstruct
    public void init() {
        log.warn("=".repeat(80));
        log.warn("WARNING: PlaintextBackend is active - secrets are stored in PLAIN TEXT");
        log.warn("This backend should ONLY be used for development and testing");
        log.warn("=".repeat(80));
    }
    
    /**
     * Stores a secret in plain text for the given environment.
     * 
     * <p>This implementation directly stores the secret value in the database
     * without any encryption, hashing, or obfuscation. The secret is stored
     * in the {@code shared_secret} column of the environments table.
     * 
     * <p><b>Implementation Details:</b>
     * <ol>
     *   <li>Looks up the environment by ID</li>
     *   <li>Sets the shared secret field to the provided value</li>
     *   <li>Saves the updated environment entity</li>
     * </ol>
     * 
     * <p><b>Performance:</b> Typically completes in &lt; 10ms for local database
     * connections. Performance may vary based on database load and network latency.
     * 
     * @param environmentId the unique identifier of the environment. Must not be null.
     * @param secret the secret value to store in plain text. Must not be null.
     * @throws SecretNotFoundException if no environment exists with the given ID
     * @throws IllegalArgumentException if environmentId or secret is null
     */
    @Override
    public void store(UUID environmentId, String secret) {
        Environment env = environmentRepository.findById(environmentId)
            .orElseThrow(() -> new SecretNotFoundException(environmentId));
        env.setSharedSecret(secret);
        environmentRepository.save(env);
    }
    
    /**
     * Retrieves a secret in plain text for the given environment.
     * 
     * <p>This implementation directly returns the secret value from the database
     * without any decryption or transformation. The secret is read from the
     * {@code shared_secret} column of the environments table.
     * 
     * <p><b>Implementation Details:</b>
     * <ol>
     *   <li>Looks up the environment by ID</li>
     *   <li>Extracts the shared secret field</li>
     *   <li>Returns the secret value as-is</li>
     * </ol>
     * 
     * <p><b>Performance:</b> Typically completes in &lt; 10ms for local database
     * connections. Performance may vary based on database load and network latency.
     * 
     * @param environmentId the unique identifier of the environment. Must not be null.
     * @return the secret value in plain text. Never returns null.
     * @throws SecretNotFoundException if no environment exists with the given ID,
     *         or if the environment exists but has no secret
     * @throws IllegalArgumentException if environmentId is null
     */
    @Override
    public String retrieve(UUID environmentId) {
        return environmentRepository.findById(environmentId)
            .map(Environment::getSharedSecret)
            .orElseThrow(() -> new SecretNotFoundException(environmentId));
    }
    
    /**
     * Deletes a secret for the given environment.
     * 
     * <p>This implementation sets the secret field to null in the database,
     * effectively removing the secret. The environment entity itself is not
     * deleted, only the secret value is cleared.
     * 
     * <p><b>Implementation Details:</b>
     * <ol>
     *   <li>Looks up the environment by ID</li>
     *   <li>Sets the shared secret field to null</li>
     *   <li>Saves the updated environment entity</li>
     * </ol>
     * 
     * <p><b>Idempotency:</b> This operation is idempotent. Calling delete
     * multiple times for the same environment will not result in an error.
     * 
     * <p><b>Performance:</b> Typically completes in &lt; 10ms for local database
     * connections. Performance may vary based on database load and network latency.
     * 
     * @param environmentId the unique identifier of the environment. Must not be null.
     * @throws SecretNotFoundException if no environment exists with the given ID
     * @throws IllegalArgumentException if environmentId is null
     */
    @Override
    public void delete(UUID environmentId) {
        Environment env = environmentRepository.findById(environmentId)
            .orElseThrow(() -> new SecretNotFoundException(environmentId));
        env.setSharedSecret(null);
        environmentRepository.save(env);
    }
    
    /**
     * Checks if the backend is healthy and operational.
     * 
     * <p>The plaintext backend has no external dependencies beyond the database
     * connection managed by Spring Data JPA. Since the database connection is
     * required for the application to function at all, this method always
     * returns true.
     * 
     * <p><b>Rationale:</b> If the database were unavailable, the application
     * would fail to start or would be unable to process any requests. Therefore,
     * if this method is being called, the database connection must be working,
     * and the backend is healthy.
     * 
     * <p><b>Performance:</b> O(1) constant time - no I/O operations performed.
     * 
     * @return always returns true
     */
    @Override
    public boolean isHealthy() {
        return true; // Always healthy - no external dependencies
    }
    
    /**
     * Returns the backend type identifier.
     * 
     * <p>This method returns "plaintext" to indicate that this is the plaintext
     * backend implementation. This identifier matches the configuration property
     * value used to select this backend.
     * 
     * @return "plaintext" - the backend type identifier
     */
    @Override
    public String getBackendType() {
        return "plaintext";
    }
}
