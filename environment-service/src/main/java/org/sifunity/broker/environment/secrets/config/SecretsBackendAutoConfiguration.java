// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.config;

import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.secrets.SecretsBackend;
import org.sifunity.broker.environment.secrets.impl.MultiKeyBackend;
import org.sifunity.broker.environment.secrets.impl.PlaintextBackend;
import org.sifunity.broker.environment.secrets.impl.VaultBackend;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.vault.core.VaultTemplate;

/**
 * Auto-configuration for secrets backend selection.
 * 
 * <p>Selects the appropriate backend implementation based on the
 * {@code secrets.backend.type} configuration property.</p>
 * 
 * <p>Default (when no type is specified): {@code multi-key} (secure by default).
 * Development and test profiles should explicitly set {@code secrets.backend.type: plaintext}.</p>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({
    SecretsBackendProperties.class,
    MultiKeyProperties.class,
    VaultProperties.class
})
public class SecretsBackendAutoConfiguration {
    
    @Bean
    @ConditionalOnProperty(
        name = "secrets.backend.type",
        havingValue = "plaintext"
    )
    public SecretsBackend plaintextBackend(EnvironmentRepository repository) {
        log.info("Configuring PlaintextBackend for secrets storage");
        return new PlaintextBackend(repository);
    }
    
    @Bean
    @ConditionalOnProperty(
        name = "secrets.backend.type",
        havingValue = "multi-key",
        matchIfMissing = true
    )
    public SecretsBackend multiKeyBackend(
            EnvironmentRepository repository,
            MultiKeyProperties properties) {
        log.info("Configuring MultiKeyBackend for secrets storage");
        return new MultiKeyBackend(repository, properties);
    }
    
    @Bean
    @ConditionalOnProperty(
        name = "secrets.backend.type",
        havingValue = "vault"
    )
    public SecretsBackend vaultBackend(
            EnvironmentRepository repository,
            VaultTemplate vaultTemplate,
            VaultProperties properties) {
        log.info("Configuring VaultBackend for secrets storage");
        return new VaultBackend(repository, vaultTemplate, properties);
    }
}
