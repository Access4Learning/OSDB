// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration that activates {@link HeaderBlacklistProperties}.
 *
 * <p>All SIF Unity Broker modules component-scan {@code org.sifunity.broker.common},
 * so this configuration is picked up automatically.</p>
 */
@Configuration
@EnableConfigurationProperties(HeaderBlacklistProperties.class)
public class HeaderBlacklistAutoConfiguration {
}
