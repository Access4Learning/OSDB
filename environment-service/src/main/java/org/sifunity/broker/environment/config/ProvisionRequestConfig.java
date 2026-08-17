// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for provision request behavior.
 *
 * <p>Binds configuration from application.yml under the prefix "sif.broker.provision-requests".</p>
 *
 * <p>Configuration structure:</p>
 * <pre>
 * sif:
 *   broker:
 *     provision-requests:
 *       auto-approve: true
 * </pre>
 *
 * @param autoApprove whether provision requests are automatically approved (default: true)
 */
@ConfigurationProperties(prefix = "sif.broker.provision-requests")
public record ProvisionRequestConfig(boolean autoApprove) {
}
