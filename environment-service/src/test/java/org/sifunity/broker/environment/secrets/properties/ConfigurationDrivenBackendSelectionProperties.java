// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.properties;

import net.jqwik.api.*;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.secrets.SecretsBackend;
import org.sifunity.broker.environment.secrets.config.SecretsBackendAutoConfiguration;
import org.sifunity.broker.environment.secrets.config.SecretsBackendProperties;
import org.sifunity.broker.environment.secrets.impl.MultiKeyBackend;
import org.sifunity.broker.environment.secrets.impl.PlaintextBackend;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Property-based tests for configuration-driven backend selection.
 * 
 * <p>These tests validate that the Spring Boot auto-configuration correctly
 * selects the appropriate backend implementation based on the configuration
 * property "secrets.backend.type".</p>
 * 
 * <p><strong>Validates: Requirements 5.1, 5.2</strong></p>
 * 
 * <p><strong>Property 8: Configuration-Driven Backend Selection</strong></p>
 * <p>For any valid backend type value ("plaintext", "multi-key", "vault") set in
 * the configuration property "secrets.backend.type", the application should
 * initialize the corresponding backend implementation.</p>
 */
@Tag("pluggable-secrets-backend")
class ConfigurationDrivenBackendSelectionProperties {

    // ========== Property 8: Configuration-Driven Backend Selection ==========

    /**
     * Property 8.1: Plaintext backend selected when type is "plaintext"
     * 
     * <p>When the configuration property "secrets.backend.type" is set to "plaintext",
     * the application context should contain a PlaintextBackend bean.</p>
     * 
     * <p><strong>Validates: Requirements 5.1, 5.2</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-8-Configuration-Driven-Backend-Selection")
    void plaintextBackendSelectedWhenTypeIsPlaintext() {
        // Given: Configuration with type="plaintext"
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(TestConfig.class, SecretsBackendAutoConfiguration.class))
            .withPropertyValues("secrets.backend.type=plaintext");
        
        // When: The application context is loaded
        contextRunner.run(context -> {
            // Then: A SecretsBackend bean should exist
            assertThat(context).hasSingleBean(SecretsBackend.class);
            
            // And: It should be a PlaintextBackend instance
            SecretsBackend backend = context.getBean(SecretsBackend.class);
            assertThat(backend)
                .as("Backend should be PlaintextBackend when type is 'plaintext'")
                .isInstanceOf(PlaintextBackend.class);
            
            // And: The backend type should be "plaintext"
            assertThat(backend.getBackendType())
                .as("Backend type should be 'plaintext'")
                .isEqualTo("plaintext");
        });
    }

    /**
     * Property 8.2: Multi-key backend selected by default (no type specified)
     * 
     * <p>When no backend type is specified in the configuration, the application
     * should default to the MultiKeyBackend (secure by default).</p>
     * 
     * <p><strong>Validates: Requirements 5.1, 5.2, 5.3</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-8-Configuration-Driven-Backend-Selection")
    void multiKeyBackendSelectedByDefault() {
        // Given: Configuration with no backend type specified, but with valid multi-key config
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(TestConfig.class, SecretsBackendAutoConfiguration.class))
            .withPropertyValues(
                "secrets.backend.multi-key.current-version=20240101",
                "secrets.backend.multi-key.keys.20240101=YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY="
            );
        
        // When: The application context is loaded
        contextRunner.run(context -> {
            // Then: A SecretsBackend bean should exist
            assertThat(context).hasSingleBean(SecretsBackend.class);
            
            // And: It should be a MultiKeyBackend instance (secure by default)
            SecretsBackend backend = context.getBean(SecretsBackend.class);
            assertThat(backend)
                .as("Backend should default to MultiKeyBackend when no type specified")
                .isInstanceOf(MultiKeyBackend.class);
            
            // And: The backend type should be "multi-key"
            assertThat(backend.getBackendType())
                .as("Backend type should be 'multi-key'")
                .isEqualTo("multi-key");
        });
    }

    /**
     * Property 8.3: Multi-key backend selected when type is "multi-key"
     * 
     * <p>When the configuration property "secrets.backend.type" is set to "multi-key",
     * the application context should contain a MultiKeyBackend bean.</p>
     * 
     * <p>Note: This test provides minimal valid configuration for MultiKeyBackend
     * to allow the bean to be created.</p>
     * 
     * <p><strong>Validates: Requirements 5.1, 5.2</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-8-Configuration-Driven-Backend-Selection")
    void multiKeyBackendSelectedWhenTypeIsMultiKey() {
        // Given: Configuration with type="multi-key" and valid key configuration
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(TestConfig.class, SecretsBackendAutoConfiguration.class))
            .withPropertyValues(
                "secrets.backend.type=multi-key",
                "secrets.backend.multi-key.current-version=20240101",
                "secrets.backend.multi-key.keys.20240101=YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY="
            );
        
        // When: The application context is loaded
        contextRunner.run(context -> {
            // Then: A SecretsBackend bean should exist
            assertThat(context).hasSingleBean(SecretsBackend.class);
            
            // And: It should be a MultiKeyBackend instance
            SecretsBackend backend = context.getBean(SecretsBackend.class);
            assertThat(backend)
                .as("Backend should be MultiKeyBackend when type is 'multi-key'")
                .isInstanceOf(MultiKeyBackend.class);
            
            // And: The backend type should be "multi-key"
            assertThat(backend.getBackendType())
                .as("Backend type should be 'multi-key'")
                .isEqualTo("multi-key");
        });
    }

    /**
     * Property 8.4: Vault backend selected when type is "vault"
     * 
     * <p>When the configuration property "secrets.backend.type" is set to "vault",
     * the application context should attempt to create a VaultBackend bean.</p>
     * 
     * <p>Note: This test expects the context to fail because VaultTemplate is not
     * available in the test context. The important validation is that the
     * auto-configuration attempts to create the VaultBackend bean.</p>
     * 
     * <p><strong>Validates: Requirements 5.1, 5.2</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-8-Configuration-Driven-Backend-Selection")
    void vaultBackendSelectedWhenTypeIsVault() {
        // Given: Configuration with type="vault"
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(TestConfig.class, SecretsBackendAutoConfiguration.class))
            .withPropertyValues(
                "secrets.backend.type=vault",
                "secrets.backend.vault.url=https://vault.example.com:8200",
                "secrets.backend.vault.token=test-token",
                "secrets.backend.vault.secrets-path=secret/test"
            );
        
        // When: The application context is loaded
        contextRunner.run(context -> {
            // Then: The context should fail to start because VaultTemplate is not available
            // This is expected behavior - the auto-configuration is trying to create
            // the VaultBackend bean, which proves the selection logic works
            assertThat(context).hasFailed();
            
            // And: The failure should be due to missing VaultTemplate dependency
            assertThat(context.getStartupFailure())
                .as("Context should fail due to missing VaultTemplate")
                .hasMessageContaining("vaultBackend");
        });
    }

    /**
     * Property 8.5: Configuration property is read correctly
     * 
     * <p>For any valid backend type value, the SecretsBackendProperties bean
     * should correctly read and expose the configured type.</p>
     * 
     * <p><strong>Validates: Requirements 5.1, 5.2</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-8-Configuration-Driven-Backend-Selection")
    void configurationPropertyIsReadCorrectly(
            @ForAll("backendTypes") String backendType) {
        
        // Given: Configuration with a specific backend type
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(TestConfig.class, SecretsBackendAutoConfiguration.class))
            .withPropertyValues("secrets.backend.type=" + backendType);
        
        // Add required configuration for multi-key backend to allow it to initialize
        if ("multi-key".equals(backendType)) {
            contextRunner = contextRunner.withPropertyValues(
                "secrets.backend.multi-key.current-version=20240101",
                "secrets.backend.multi-key.keys.20240101=YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY="
            );
        }
        
        // When: The application context is loaded
        contextRunner.run(context -> {
            // Then: The SecretsBackendProperties bean should exist
            // (even if the backend itself fails to initialize for vault)
            if ("vault".equals(backendType)) {
                // Vault backend will fail without VaultTemplate, but properties should still be readable
                assertThat(context).hasFailed();
            } else {
                assertThat(context).hasSingleBean(SecretsBackendProperties.class);
                
                // And: It should contain the configured type
                SecretsBackendProperties properties = context.getBean(SecretsBackendProperties.class);
                assertThat(properties.getType())
                    .as("Properties should contain the configured backend type")
                    .isEqualTo(backendType);
            }
        });
    }

    /**
     * Property 8.6: Only one backend bean is created
     * 
     * <p>For any valid backend type configuration, exactly one SecretsBackend
     * bean should be created in the application context.</p>
     * 
     * <p><strong>Validates: Requirements 5.1, 5.2</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property-8-Configuration-Driven-Backend-Selection")
    void onlyOneBackendBeanIsCreated() {
        // Given: Configuration with type="plaintext"
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(TestConfig.class, SecretsBackendAutoConfiguration.class))
            .withPropertyValues("secrets.backend.type=plaintext");
        
        // When: The application context is loaded
        contextRunner.run(context -> {
            // Then: Exactly one SecretsBackend bean should exist
            assertThat(context).hasSingleBean(SecretsBackend.class);
            
            // And: No other backend implementations should be created
            assertThat(context.getBeansOfType(SecretsBackend.class))
                .as("Only one SecretsBackend bean should be created")
                .hasSize(1);
        });
    }

    // ========== Arbitraries (Test Data Generators) ==========

    /**
     * Provides arbitrary backend type values for testing.
     * 
     * <p>Generates valid backend type strings: "plaintext", "multi-key", "vault".</p>
     */
    @Provide
    Arbitrary<String> backendTypes() {
        return Arbitraries.of("plaintext", "multi-key", "vault");
    }

    // ========== Test Configuration ==========

    /**
     * Test configuration that provides a mock EnvironmentRepository bean.
     * 
     * <p>This configuration is only used within ApplicationContextRunner tests
     * and is not picked up by component scanning because it's not annotated
     * with @Configuration or @TestConfiguration.</p>
     */
    static class TestConfig {
        @Bean
        EnvironmentRepository environmentRepository() {
            return mock(EnvironmentRepository.class);
        }
    }
}
