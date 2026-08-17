// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets;

/**
 * Exception thrown when secrets backend configuration is invalid.
 * 
 * <p>This exception is thrown during backend initialization when the configuration
 * is invalid, incomplete, or inconsistent. It prevents the application from starting
 * with an invalid secrets backend configuration, following the "fail fast" principle.
 * 
 * <p><b>Common Scenarios:</b>
 * <ul>
 *   <li><b>MultiKeyBackend:</b>
 *     <ul>
 *       <li>No encryption keys configured</li>
 *       <li>Current key version not specified</li>
 *       <li>Current key version not found in configured keys</li>
 *       <li>Encryption key not in valid base64 format</li>
 *       <li>Encryption key has incorrect length (not 256 bits)</li>
 *       <li>Key version not in YYYYMMDD format</li>
 *     </ul>
 *   </li>
 *   <li><b>VaultBackend:</b>
 *     <ul>
 *       <li>Vault URL not specified or invalid</li>
 *       <li>Vault authentication token not provided</li>
 *       <li>Vault secrets path not specified</li>
 *       <li>Invalid TLS configuration</li>
 *     </ul>
 *   </li>
 *   <li><b>General:</b>
 *     <ul>
 *       <li>Invalid backend type specified (not "plaintext", "multi-key", or "vault")</li>
 *       <li>Required configuration properties missing</li>
 *       <li>Conflicting configuration values</li>
 *     </ul>
 *   </li>
 * </ul>
 * 
 * <p><b>Error Handling:</b> This exception is typically thrown during application
 * startup and should cause the application to fail to start. This is intentional
 * behavior to prevent running with an insecure or non-functional secrets backend.
 * 
 * <p>Operators should:
 * <ul>
 *   <li>Review the exception message for specific configuration issues</li>
 *   <li>Verify all required configuration properties are set</li>
 *   <li>Check environment variables for sensitive configuration values</li>
 *   <li>Validate configuration file syntax (YAML formatting)</li>
 *   <li>Ensure encryption keys are properly base64-encoded</li>
 *   <li>Verify Vault connectivity and authentication</li>
 * </ul>
 * 
 * <p><b>Security Considerations:</b> Exception messages should describe what
 * configuration is invalid but should not include sensitive values such as:
 * <ul>
 *   <li>Encryption keys</li>
 *   <li>Vault authentication tokens</li>
 *   <li>Database passwords</li>
 * </ul>
 * 
 * <p>Instead, messages should indicate which configuration property is invalid
 * and what format is expected.
 * 
 * <p><b>Example Usage:</b>
 * <pre>
 * public MultiKeyBackend(EnvironmentRepository repository, MultiKeyProperties properties) {
 *     this.environmentRepository = repository;
 *     this.keyVersions = loadKeys(properties);
 *     this.currentKeyVersion = properties.getCurrentVersion();
 *     
 *     if (keyVersions.isEmpty()) {
 *         throw new ConfigurationException("No encryption keys configured for MultiKeyBackend");
 *     }
 *     if (currentKeyVersion == null || currentKeyVersion.isEmpty()) {
 *         throw new ConfigurationException("Current key version not specified");
 *     }
 *     if (!keyVersions.containsKey(currentKeyVersion)) {
 *         throw new ConfigurationException(
 *             "Current key version '" + currentKeyVersion + "' not found in configured keys");
 *     }
 * }
 * </pre>
 * 
 * @see SecretsBackend
 * @since 1.0
 */
public class ConfigurationException extends SecretsBackendException {
    
    /**
     * Constructs a new configuration exception with the specified detail message.
     * 
     * <p>The message should clearly describe what configuration is invalid or missing,
     * and ideally suggest how to fix it. The message should not include sensitive
     * values such as encryption keys or authentication tokens.
     * 
     * <p><b>Good message examples:</b>
     * <ul>
     *   <li>"No encryption keys configured for MultiKeyBackend"</li>
     *   <li>"Current key version not found in configured keys: 20240115"</li>
     *   <li>"Vault URL not specified in configuration"</li>
     *   <li>"Encryption key must be base64-encoded 256-bit value"</li>
     * </ul>
     * 
     * <p><b>Bad message examples (include sensitive data):</b>
     * <ul>
     *   <li>"Invalid encryption key: abc123..." (exposes key)</li>
     *   <li>"Vault token xyz789... is invalid" (exposes token)</li>
     * </ul>
     * 
     * @param message the detail message explaining the configuration problem. Should
     *                not contain sensitive data such as keys or tokens.
     */
    public ConfigurationException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new configuration exception with the specified detail message and cause.
     * 
     * <p>This constructor is useful for wrapping lower-level exceptions that occur
     * during configuration loading or validation (such as parsing errors, format
     * exceptions, or validation failures) while providing additional context about
     * the configuration problem.
     * 
     * <p><b>Example:</b>
     * <pre>
     * try {
     *     byte[] keyBytes = Base64.getDecoder().decode(keyString);
     *     if (keyBytes.length != 32) {
     *         throw new ConfigurationException("Encryption key must be 256 bits (32 bytes)");
     *     }
     *     return new SecretKeySpec(keyBytes, "AES");
     * } catch (IllegalArgumentException e) {
     *     throw new ConfigurationException("Encryption key is not valid base64", e);
     * }
     * </pre>
     * 
     * @param message the detail message explaining the configuration problem. Should
     *                not contain sensitive data such as keys or tokens.
     * @param cause the underlying exception that caused the configuration failure
     *              (e.g., parsing exception, validation exception). May be null if
     *              no underlying cause exists.
     */
    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
