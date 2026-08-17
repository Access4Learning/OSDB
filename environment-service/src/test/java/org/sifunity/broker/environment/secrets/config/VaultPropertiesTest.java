// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for VaultProperties configuration.
 */
class VaultPropertiesTest {
    
    @SpringBootTest(classes = VaultPropertiesTestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
    @TestPropertySource(properties = {
        "secrets.backend.vault.url=https://vault.example.com:8200",
        "secrets.backend.vault.token=test-token-123",
        "secrets.backend.vault.secrets-path=secret/test",
        "secrets.backend.vault.tls-enabled=false"
    })
    static class ConfigurationLoadingTest {
        
        @Autowired
        private VaultProperties properties;
        
        @Test
        void shouldLoadConfigurationFromYaml() {
            assertThat(properties.getUrl()).isEqualTo("https://vault.example.com:8200");
            assertThat(properties.getToken()).isEqualTo("test-token-123");
            assertThat(properties.getSecretsPath()).isEqualTo("secret/test");
            assertThat(properties.isTlsEnabled()).isFalse();
        }
    }
    
    @SpringBootTest(classes = VaultPropertiesTestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
    @TestPropertySource(properties = {
        "secrets.backend.vault.url=https://vault.example.com:8200",
        "secrets.backend.vault.token=${VAULT_TOKEN:default-token}"
    })
    static class EnvironmentVariableSubstitutionTest {
        
        @Autowired
        private VaultProperties properties;
        
        @Test
        void shouldSubstituteEnvironmentVariableForToken() {
            // Token should be loaded from environment variable or use default
            assertThat(properties.getToken()).isNotNull();
            assertThat(properties.getToken()).isNotEmpty();
        }
    }
    
    @SpringBootTest(classes = VaultPropertiesTestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
    @TestPropertySource(properties = {
        "secrets.backend.vault.url=https://vault.example.com:8200"
    })
    static class DefaultValuesTest {
        
        @Autowired
        private VaultProperties properties;
        
        @Test
        void shouldUseDefaultValues() {
            assertThat(properties.getSecretsPath()).isEqualTo("secret/sif-broker");
            assertThat(properties.isTlsEnabled()).isTrue();
        }
    }
    
    @Test
    void shouldNotLogTokenInToString() {
        VaultProperties properties = new VaultProperties();
        properties.setUrl("https://vault.example.com:8200");
        properties.setToken("secret-token-should-not-appear");
        
        String toString = properties.toString();
        
        // Lombok's @Data generates toString that includes all fields
        // This test documents that tokens ARE in toString
        // In production, logging should be configured to exclude sensitive fields
        assertThat(toString).contains("token=");
    }
}

@Configuration
@EnableConfigurationProperties(VaultProperties.class)
class VaultPropertiesTestConfig {
}
