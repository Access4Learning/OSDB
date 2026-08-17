// SPDX-License-Identifier: MIT
package org.sifunity.integration.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Configuration class for ADMIN credentials used in integration tests.
 * 
 * <p>This class provides access to the bootstrap ADMIN credentials that are
 * configured in the environment-service's application-test.yml. These credentials
 * are used by integration tests to perform administrative operations like:
 * <ul>
 *   <li>Creating test environments</li>
 *   <li>Creating ACL entries</li>
 *   <li>Deleting test data</li>
 * </ul>
 * 
 * <p>The ADMIN credentials are read from the environment-service bootstrap
 * configuration and should match the values in:
 * {@code environment-service/src/test/resources/application-test.yml}
 * 
 * <p>Example usage:
 * <pre>
 * AdminCredentialsConfig adminConfig = new AdminCredentialsConfig();
 * String sessionToken = adminConfig.getSessionToken();
 * String sharedSecret = adminConfig.getSharedSecret();
 * 
 * // Use credentials for authenticated requests
 * environmentGenerator.createEnvironment(sessionToken, sharedSecret, ...);
 * </pre>
 * 
 * @since 1.0.0
 */
@Slf4j
@Getter
public class AdminCredentialsConfig {
    
    /**
     * ADMIN session token from bootstrap configuration.
     * This value must match the value in environment-service's application-test.yml:
     * sif.broker.bootstrap.admin.sessionToken
     */
    private final String sessionToken;
    
    /**
     * ADMIN shared secret from bootstrap configuration.
     * This value must match the value in environment-service's application-test.yml:
     * sif.broker.bootstrap.admin.sharedSecret
     */
    private final String sharedSecret;
    
    /**
     * Creates a new ADMIN credentials configuration.
     * 
     * <p>The credentials are hardcoded to match the bootstrap configuration
     * in environment-service's application-test.yml. This ensures consistency
     * between the bootstrap ADMIN and the credentials used in integration tests.
     * 
     * <p>Note: The sharedSecret is Base64 encoded to match the format expected
     * by the HMAC authentication mechanism.
     */
    public AdminCredentialsConfig() {
        // These values must match environment-service/src/test/resources/application-test.yml
        // sif.broker.bootstrap.admin.sessionToken and sharedSecret
        this.sessionToken = "test-admin-token";
        this.sharedSecret = "dGVzdC1hZG1pbi1zZWNyZXQ=";  // Base64 encoded "test-admin-secret"
        
        log.debug("ADMIN credentials configured: sessionToken={}", sessionToken);
    }
    
    /**
     * Creates a new ADMIN credentials configuration with custom values.
     * 
     * <p>This constructor is useful for tests that need to override the default
     * credentials or for production environments where credentials are loaded
     * from environment variables or configuration files.
     * 
     * @param sessionToken the ADMIN session token
     * @param sharedSecret the ADMIN shared secret
     */
    public AdminCredentialsConfig(String sessionToken, String sharedSecret) {
        this.sessionToken = sessionToken;
        this.sharedSecret = sharedSecret;
        
        log.debug("ADMIN credentials configured with custom values: sessionToken={}", sessionToken);
    }
}
