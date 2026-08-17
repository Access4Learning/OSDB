// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Configuration properties for the Multi-Key encryption backend.
 * 
 * <p>This class binds configuration properties from the application YAML file
 * under the prefix {@code secrets.backend.multi-key}. It provides configuration
 * for AES-256-GCM encryption with support for multiple key versions to enable
 * key rotation without downtime.
 * 
 * <p><b>Configuration Example:</b>
 * <pre>
 * secrets:
 *   backend:
 *     type: multi-key
 *     multi-key:
 *       current-version: "20240115"
 *       keys:
 *         "20240115": "${ENCRYPTION_KEY_20240115}"
 *         "20240101": "${ENCRYPTION_KEY_20240101}"
 * </pre>
 * 
 * <p><b>Key Format:</b> Encryption keys must be base64-encoded 256-bit (32-byte)
 * values. Keys should be loaded from environment variables to avoid hardcoding
 * sensitive values in configuration files.
 * 
 * <p><b>Version Format:</b> Key versions use YYYYMMDD format (e.g., "20240115")
 * to indicate when the key was created or activated. This format is used as a
 * prefix in encrypted values stored in the database.
 * 
 * <p><b>Key Rotation:</b> To rotate keys:
 * <ol>
 *   <li>Add the new key to the {@code keys} map</li>
 *   <li>Update {@code currentVersion} to the new key's version</li>
 *   <li>Keep old keys in the map to decrypt existing secrets</li>
 *   <li>Run the migration job to re-encrypt secrets with the new key</li>
 *   <li>Remove old keys once migration is complete</li>
 * </ol>
 * 
 * <p><b>Security Considerations:</b>
 * <ul>
 *   <li>Never hardcode keys in configuration files</li>
 *   <li>Use environment variables or secret management systems</li>
 *   <li>Rotate keys regularly (e.g., every 90 days)</li>
 *   <li>Keep old keys until all secrets are migrated</li>
 *   <li>Use strong random keys (256 bits of entropy)</li>
 * </ul>
 * 
 * @see org.sifunity.broker.environment.secrets.impl.MultiKeyBackend
 * @since 1.0
 */
@Data
@ConfigurationProperties(prefix = "secrets.backend.multi-key")
public class MultiKeyProperties {
    
    /**
     * Current key version in YYYYMMDD format.
     * 
     * <p>This version is used for encrypting new secrets. It must match one of
     * the keys in the {@code keys} map. When storing a secret, the encrypted
     * value is prefixed with this version (e.g., "20240115:encrypted_data").
     * 
     * <p><b>Example:</b> "20240115"
     * 
     * <p><b>Validation:</b> Must not be null and must exist in the {@code keys} map.
     */
    private String currentVersion;
    
    /**
     * Map of key versions to base64-encoded 256-bit encryption keys.
     * 
     * <p>Each entry maps a version string (YYYYMMDD format) to a base64-encoded
     * AES-256 key. Multiple versions can be configured simultaneously to support
     * key rotation without downtime.
     * 
     * <p><b>Key Format:</b> Base64-encoded 256-bit (32-byte) values
     * 
     * <p><b>Example:</b>
     * <pre>
     * keys:
     *   "20240115": "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY="
     *   "20240101": "MTIzNDU2Nzg5MGFiY2RlZmdoaWprbG1ub3BxcnN0dXY="
     * </pre>
     * 
     * <p><b>Validation:</b> Must not be null or empty. Each key must be a valid
     * base64-encoded 256-bit value.
     */
    private Map<String, String> keys;
}
