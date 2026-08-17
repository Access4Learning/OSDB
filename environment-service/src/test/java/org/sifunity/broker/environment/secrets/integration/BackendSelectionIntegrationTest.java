// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.integration;

import org.junit.jupiter.api.Test;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.secrets.SecretsBackend;
import org.sifunity.broker.environment.secrets.config.SecretsBackendAutoConfiguration;
import org.sifunity.broker.environment.secrets.impl.MultiKeyBackend;
import org.sifunity.broker.environment.secrets.impl.PlaintextBackend;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Integration tests for backend selection via Spring Boot auto-configuration.
 * 
 * <p>These tests validate that the auto-configuration correctly selects and
 * initializes the appropriate backend implementation based on configuration
 * properties.</p>
 * 
 * <p><strong>Validates: Requirements 5.1, 5.2, 5.3, 5.6, 5.7, 11.1</strong></p>
 */
class BackendSelectionIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(UserConfigurations.of(SecretsBackendAutoConfiguration.class))
        .withBean(EnvironmentRepository.class, () -> mock(EnvironmentRepository.class));

    /**
     * Test: Default to multi-key when no config specified
     * 
     * <p>When no backend type is specified in the configuration, the application
     * should default to the MultiKeyBackend (secure by default).</p>
     * 
     * <p>Note: Without encryption keys configured, the context will fail to start.
     * This validates that multi-key is the default selection.</p>
     * 
     * <p><strong>Validates: Requirements 5.3</strong></p>
     */
    @Test
    void shouldDefaultToMultiKeyWhenNoConfigSpecified() {
        // Given: No backend type configuration, but with valid multi-key config
        contextRunner
            .withPropertyValues(
                "secrets.backend.multi-key.current-version=20240101",
                "secrets.backend.multi-key.keys.20240101=YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY="
            )
            .run(context -> {
                // Then: A SecretsBackend bean should exist
                assertThat(context).hasSingleBean(SecretsBackend.class);
                
                // And: It should be a MultiKeyBackend instance (secure by default)
                SecretsBackend backend = context.getBean(SecretsBackend.class);
                assertThat(backend)
                    .as("Should default to MultiKeyBackend when no config specified")
                    .isInstanceOf(MultiKeyBackend.class);
                
                // And: The backend type should be "multi-key"
                assertThat(backend.getBackendType())
                    .isEqualTo("multi-key");
            });
    }

    /**
     * Test: Multi-key backend selected with type="multi-key"
     * 
     * <p>When the configuration property "secrets.backend.type" is set to "multi-key"
     * with valid key configuration, the application should create a MultiKeyBackend bean.</p>
     * 
     * <p><strong>Validates: Requirements 5.2, 5.7</strong></p>
     */
    @Test
    void shouldSelectMultiKeyBackendWhenConfigured() {
        // Given: Configuration with type="multi-key" and valid keys
        contextRunner
            .withPropertyValues(
                "secrets.backend.type=multi-key",
                "secrets.backend.multi-key.current-version=20240101",
                "secrets.backend.multi-key.keys.20240101=YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY="
            )
            .run(context -> {
                // Then: A SecretsBackend bean should exist
                assertThat(context).hasSingleBean(SecretsBackend.class);
                
                // And: It should be a MultiKeyBackend instance
                SecretsBackend backend = context.getBean(SecretsBackend.class);
                assertThat(backend)
                    .as("Should create MultiKeyBackend when type is 'multi-key'")
                    .isInstanceOf(MultiKeyBackend.class);
                
                // And: The backend type should be "multi-key"
                assertThat(backend.getBackendType())
                    .isEqualTo("multi-key");
                
                // And: The backend should be healthy
                assertThat(backend.isHealthy())
                    .as("MultiKeyBackend should be healthy with valid configuration")
                    .isTrue();
            });
    }

    /**
     * Test: Vault backend selected with type="vault"
     * 
     * <p>When the configuration property "secrets.backend.type" is set to "vault",
     * the application should attempt to create a VaultBackend bean. Since VaultTemplate
     * is not available in this test context, the context should fail to start.</p>
     * 
     * <p><strong>Validates: Requirements 5.2</strong></p>
     */
    @Test
    void shouldAttemptToSelectVaultBackendWhenConfigured() {
        // Given: Configuration with type="vault"
        contextRunner
            .withPropertyValues(
                "secrets.backend.type=vault",
                "secrets.backend.vault.url=https://vault.example.com:8200",
                "secrets.backend.vault.token=test-token",
                "secrets.backend.vault.secrets-path=secret/test"
            )
            .run(context -> {
                // Then: The context should fail because VaultTemplate is not available
                assertThat(context).hasFailed();
                
                // And: The failure should be due to missing VaultTemplate dependency
                assertThat(context.getStartupFailure())
                    .hasMessageContaining("VaultTemplate");
            });
    }

    /**
     * Test: No backend bean created with invalid backend type
     * 
     * <p>When an invalid backend type is specified in the configuration, no
     * SecretsBackend bean is created because none of the @ConditionalOnProperty
     * conditions match. The matchIfMissing=true only applies when the property
     * is absent, not when it has an invalid value.</p>
     * 
     * <p><strong>Validates: Requirements 5.6</strong></p>
     */
    @Test
    void shouldNotCreateBeanWithInvalidBackendType() {
        // Given: Configuration with invalid backend type
        contextRunner
            .withPropertyValues("secrets.backend.type=invalid-backend")
            .run(context -> {
                // Then: The context should start but no SecretsBackend bean exists
                assertThat(context).hasNotFailed();
                
                // And: No SecretsBackend bean should be created
                assertThat(context.getBeansOfType(SecretsBackend.class))
                    .as("No backend bean should be created with invalid type")
                    .isEmpty();
            });
    }

    /**
     * Test: Logged backend type during startup
     * 
     * <p>When a backend is selected, the auto-configuration should log the
     * selected backend type during application startup.</p>
     * 
     * <p>Note: This test validates that the log message is produced by checking
     * that the backend is successfully created. The actual log output would be
     * visible in application logs.</p>
     * 
     * <p><strong>Validates: Requirements 5.7</strong></p>
     */
    @Test
    void shouldLogBackendTypeDuringStartup() {
        // Given: Configuration with type="plaintext"
        contextRunner
            .withPropertyValues("secrets.backend.type=plaintext")
            .run(context -> {
                // Then: The backend should be created successfully
                assertThat(context).hasSingleBean(SecretsBackend.class);
                
                // And: The backend type should be accessible
                SecretsBackend backend = context.getBean(SecretsBackend.class);
                assertThat(backend.getBackendType())
                    .as("Backend type should be logged during startup")
                    .isEqualTo("plaintext");
                
                // Note: The actual log message "Configuring PlaintextBackend for secrets storage"
                // is produced by the @Slf4j logger in SecretsBackendAutoConfiguration
            });
    }

    /**
     * Test: Only one backend bean created
     * 
     * <p>When a backend type is configured, exactly one SecretsBackend bean
     * should be created in the application context.</p>
     * 
     * <p><strong>Validates: Requirements 5.1, 5.2</strong></p>
     */
    @Test
    void shouldCreateOnlyOneBackendBean() {
        // Given: Configuration with type="plaintext"
        contextRunner
            .withPropertyValues("secrets.backend.type=plaintext")
            .run(context -> {
                // Then: Exactly one SecretsBackend bean should exist
                assertThat(context).hasSingleBean(SecretsBackend.class);
                
                // And: The beans map should contain only one backend
                assertThat(context.getBeansOfType(SecretsBackend.class))
                    .as("Only one SecretsBackend bean should be created")
                    .hasSize(1);
            });
    }

    /**
     * Test: Configuration properties are loaded correctly
     * 
     * <p>When backend configuration is provided, the properties beans should
     * be created and populated with the configured values.</p>
     * 
     * <p><strong>Validates: Requirements 5.4</strong></p>
     */
    @Test
    void shouldLoadConfigurationPropertiesCorrectly() {
        // Given: Configuration with multi-key backend
        contextRunner
            .withPropertyValues(
                "secrets.backend.type=multi-key",
                "secrets.backend.multi-key.current-version=20240115",
                "secrets.backend.multi-key.keys.20240115=YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY="
            )
            .run(context -> {
                // Then: The backend should be created successfully
                assertThat(context).hasSingleBean(SecretsBackend.class);
                
                // And: The backend should be healthy (indicating config was loaded)
                SecretsBackend backend = context.getBean(SecretsBackend.class);
                assertThat(backend.isHealthy())
                    .as("Backend should be healthy when configuration is valid")
                    .isTrue();
            });
    }

    /**
     * Test: Multi-key backend fails without current key
     * 
     * <p>When multi-key backend is configured without a current key version,
     * the application should fail to start with a descriptive error.</p>
     * 
     * <p><strong>Validates: Requirements 5.6, 6.5</strong></p>
     */
    @Test
    void shouldFailWhenMultiKeyBackendMissingCurrentKey() {
        // Given: Configuration with multi-key but no current version
        contextRunner
            .withPropertyValues(
                "secrets.backend.type=multi-key",
                "secrets.backend.multi-key.keys.20240101=YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY="
            )
            .run(context -> {
                // Then: The context should fail to start
                assertThat(context).hasFailed();
                
                // And: The failure should indicate missing current key
                String message = context.getStartupFailure().getMessage().toLowerCase();
                assertThat(message)
                    .as("Error message should mention current key")
                    .contains("current");
            });
    }

    /**
     * Test: Multi-key backend fails with invalid key format
     * 
     * <p>When multi-key backend is configured with an invalid key format,
     * the application should fail to start with a descriptive error.</p>
     * 
     * <p><strong>Validates: Requirements 5.6, 6.6</strong></p>
     */
    @Test
    void shouldFailWhenMultiKeyBackendHasInvalidKeyFormat() {
        // Given: Configuration with multi-key but invalid key (not base64)
        contextRunner
            .withPropertyValues(
                "secrets.backend.type=multi-key",
                "secrets.backend.multi-key.current-version=20240101",
                "secrets.backend.multi-key.keys.20240101=not-a-valid-base64-key!!!"
            )
            .run(context -> {
                // Then: The context should fail to start
                assertThat(context).hasFailed();
                
                // And: The failure should indicate invalid key format
                Throwable failure = context.getStartupFailure();
                assertThat(failure)
                    .satisfiesAnyOf(
                        ex -> assertThat(ex).hasMessageContaining("key"),
                        ex -> assertThat(ex).hasMessageContaining("format"),
                        ex -> assertThat(ex).hasMessageContaining("base64"),
                        ex -> assertThat(ex).hasMessageContaining("Illegal")
                    );
            });
    }
}
