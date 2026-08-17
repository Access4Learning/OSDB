// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for secrets backend selection.
 * 
 * <p>Supports three backend types:
 * <ul>
 *   <li>plaintext - No encryption (development only)</li>
 *   <li>multi-key - AES-256-GCM with key rotation</li>
 *   <li>vault - HashiCorp Vault integration</li>
 * </ul>
 */
@Data
@ConfigurationProperties(prefix = "secrets.backend")
public class SecretsBackendProperties {
    /**
     * Backend type: plaintext, multi-key, or vault.
     * Defaults to plaintext if not specified.
     */
    private String type = "plaintext";
}
