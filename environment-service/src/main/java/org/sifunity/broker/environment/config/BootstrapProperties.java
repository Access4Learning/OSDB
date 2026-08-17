// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for bootstrap ADMIN environment creation.
 * 
 * <p>Binds configuration from application.yml under the prefix "sif.broker.bootstrap"
 * to provide type-safe access to bootstrap settings.</p>
 * 
 * <p>Configuration structure:</p>
 * <pre>
 * sif:
 *   broker:
 *     bootstrap:
 *       enabled: true
 *       admin:
 *         sessionToken: "admin-session-token"
 *         sharedSecret: "admin-shared-secret"
 * </pre>
 * 
 * <p>The bootstrap mechanism creates an ADMIN environment on application startup
 * if one doesn't already exist. This ensures administrative operations can be
 * performed immediately after deployment.</p>
 * 
 * @see org.sifunity.broker.environment.service.BootstrapComponent
 */
@Component
@ConfigurationProperties(prefix = "sif.broker.bootstrap")
@Data
public class BootstrapProperties {
    
    /**
     * Whether bootstrap is enabled.
     * Default is true - bootstrap will run on startup.
     * Set to false to disable automatic ADMIN creation.
     */
    private boolean enabled = true;
    
    /**
     * ADMIN environment credentials for bootstrap.
     * Contains sessionToken and sharedSecret for the ADMIN environment.
     */
    private AdminCredentials admin;
    
    /**
     * Nested configuration class for ADMIN credentials.
     */
    @Data
    public static class AdminCredentials {
        /**
         * Session token for the ADMIN environment.
         * Must be unique and should be treated as a secret.
         */
        private String sessionToken;
        
        /**
         * Shared secret for HMAC authentication.
         * Must be cryptographically secure and treated as a secret.
         */
        private String sharedSecret;
    }
}
