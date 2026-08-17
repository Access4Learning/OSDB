// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Vault backend.
 * 
 * <p>Loads Vault connection details from application configuration.
 * Authentication token should be provided via environment variable.
 */
@Data
@ConfigurationProperties(prefix = "secrets.backend.vault")
public class VaultProperties {
    
    /**
     * Vault server URL (e.g., "https://vault.example.com:8200")
     */
    private String url;
    
    /**
     * Vault authentication token (should be loaded from environment variable)
     */
    private String token;
    
    /**
     * Base path for secrets storage in Vault
     */
    private String secretsPath = "secret/sif-broker";
    
    /**
     * Enable TLS certificate validation for Vault connections
     */
    private boolean tlsEnabled = true;
}
